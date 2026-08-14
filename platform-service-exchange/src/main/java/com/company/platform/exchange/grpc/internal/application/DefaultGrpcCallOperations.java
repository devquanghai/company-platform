package com.company.platform.exchange.grpc.internal.application;

import com.company.platform.exchange.client.internal.application.ClientConfigurationResolver;
import com.company.platform.core.context.RequestContextProvider;
import com.company.platform.core.time.TimeProvider;
import com.company.platform.core.trace.CurrentTraceContext;
import com.company.platform.core.trace.TraceContextProvider;
import com.company.platform.exchange.api.grpc.GrpcCallOperations;
import com.company.platform.exchange.api.grpc.GrpcCallRequest;
import com.company.platform.exchange.audit.event.OutboundCallAttemptEvent;
import com.company.platform.exchange.audit.event.OutboundCallCompletedEvent;
import com.company.platform.exchange.audit.event.OutboundCallEvent;
import com.company.platform.exchange.audit.event.OutboundCallEventData;
import com.company.platform.exchange.audit.event.OutboundCallFailedEvent;
import com.company.platform.exchange.audit.event.OutboundCallStartedEvent;
import com.company.platform.exchange.audit.publisher.OutboundCallEventPublisher;
import com.company.platform.exchange.autoconfigure.properties.AuditFailureMode;
import com.company.platform.exchange.autoconfigure.properties.ClientProperties;
import com.company.platform.exchange.domain.exception.OutboundCallException;
import com.company.platform.exchange.domain.exception.OutboundFallbackException;
import com.company.platform.exchange.domain.exception.OutboundGrpcException;
import com.company.platform.exchange.domain.exception.SanitizedRemoteCauseException;
import com.company.platform.exchange.domain.model.ExchangeProtocol;
import com.company.platform.exchange.domain.policy.RetryContext;
import com.company.platform.exchange.domain.policy.RetryDecisionPolicy;
import com.company.platform.exchange.observability.logging.OutboundDataMasker;
import com.company.platform.exchange.observability.metrics.ExchangeMetrics;
import com.company.platform.exchange.resilience.executor.ResilienceExecutionContext;
import com.company.platform.exchange.resilience.executor.ResilienceExecutor;
import com.company.platform.exchange.resilience.fallback.FallbackContext;
import com.company.platform.exchange.resilience.fallback.OutboundFallbackRegistry;
import io.grpc.Context;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

public final class DefaultGrpcCallOperations implements GrpcCallOperations, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger("OUTBOUND_CALL");

    private final ClientConfigurationResolver configurations;
    private final RetryDecisionPolicy retryPolicy;
    private final ResilienceExecutor resilience;
    private final OutboundFallbackRegistry fallbacks;
    private final OutboundCallEventPublisher events;
    private final OutboundDataMasker masker;
    private final ExchangeMetrics metrics;
    private final TimeProvider time;
    private final RequestContextProvider requestContext;
    private final TraceContextProvider traceContext;
    private final String sourceApplication;
    private final ScheduledExecutorService deadlines =
        Executors.newSingleThreadScheduledExecutor(
            Thread.ofVirtual().name("platform-exchange-grpc-deadline-", 0).factory());

    public DefaultGrpcCallOperations(
        ClientConfigurationResolver configurations,
        RetryDecisionPolicy retryPolicy, ResilienceExecutor resilience
    ) {
        this(configurations, retryPolicy, resilience, null, null, null,
            null, null, null, null, "unknown");
    }

    public DefaultGrpcCallOperations(
        ClientConfigurationResolver configurations, RetryDecisionPolicy retryPolicy,
        ResilienceExecutor resilience, OutboundFallbackRegistry fallbacks,
        OutboundCallEventPublisher events, OutboundDataMasker masker,
        ExchangeMetrics metrics, TimeProvider time,
        RequestContextProvider requestContext, TraceContextProvider traceContext,
        String sourceApplication
    ) {
        this.configurations = configurations;
        this.retryPolicy = retryPolicy;
        this.resilience = resilience;
        this.fallbacks = fallbacks;
        this.events = events;
        this.masker = masker;
        this.metrics = metrics;
        this.time = time;
        this.requestContext = requestContext;
        this.traceContext = traceContext;
        this.sourceApplication = sourceApplication;
    }

    @Override
    public <T> T execute(
        String clientName, String serviceName, String methodName, Supplier<T> invocation
    ) {
        return execute(GrpcCallRequest.builder().clientName(clientName)
            .serviceName(serviceName).methodName(methodName)
            .deadline(Duration.ofSeconds(5)).idempotent(false).build(), invocation);
    }

    @Override
    public <T> T execute(GrpcCallRequest request, Supplier<T> invocation) {
        ClientProperties client = configurations.resolve(
            request.getClientName(), ExchangeProtocol.GRPC);
        Duration deadline = request.getDeadline();
        if (deadline == null || deadline.isZero() || deadline.isNegative()) {
            throw new IllegalArgumentException(
                "An explicit positive gRPC logical-call deadline is required");
        }
        Instant started = nowInstant();
        AtomicInteger attempts = new AtomicInteger();
        publish(client, new OutboundCallStartedEvent(
            eventData(request, started, attempts.get(), false, null, null)));
        logStarted(client, request);
        Context.CancellableContext context = Context.current().withDeadlineAfter(
            deadline.toNanos(), TimeUnit.NANOSECONDS, deadlines);
        try {
            T result = context.call(() -> {
                Supplier<T> call = () -> invoke(client, request, deadline, attempts, invocation);
                if (!client.isResilienceEnabled()
                    || Boolean.FALSE.equals(request.getResilienceEnabled())) {
                    return call.get();
                }
                return resilience.execute(ResilienceExecutionContext.builder()
                    .clientName(request.getClientName())
                    .operation(operation(request)).build(), call);
            });
            Duration duration = Duration.between(started, nowInstant());
            publish(client, new OutboundCallCompletedEvent(
                eventData(request, started, attempts.get(), false, null, Status.Code.OK)));
            record(request, duration, attempts.get(), false, null, Status.Code.OK);
            logFinal(request, duration, attempts.get(), false, null);
            return result;
        } catch (OutboundCallException exception) {
            return fallbackOrThrow(request, client, started, attempts.get(), exception);
        } catch (Exception exception) {
            OutboundGrpcException normalized = new OutboundGrpcException(
                request.getClientName(), request.getServiceName(), request.getMethodName(),
                Status.Code.UNKNOWN, Map.of(), Math.max(0, attempts.get() - 1),
                deadline, false, new SanitizedRemoteCauseException(exception));
            return fallbackOrThrow(request, client, started, attempts.get(), normalized);
        } finally {
            context.cancel(null);
        }
    }

    private <T> T invoke(
        ClientProperties client, GrpcCallRequest request, Duration deadline,
        AtomicInteger attempts, Supplier<T> invocation
    ) {
        if (logicalDeadlineExpired()) {
            throw new OutboundGrpcException(
                request.getClientName(), request.getServiceName(), request.getMethodName(),
                Status.Code.DEADLINE_EXCEEDED, Map.of(),
                Math.max(0, attempts.get() - 1), deadline, false,
                new SanitizedRemoteCauseException(
                    new java.util.concurrent.TimeoutException("logical deadline expired")));
        }
        int attempt = attempts.incrementAndGet();
        if (client.getAudit().isPublishAttemptEvents()) {
            publish(client, new OutboundCallAttemptEvent(
                eventData(request, nowInstant(), attempt, false, null, null)));
        }
        try {
            return invocation.get();
        } catch (StatusRuntimeException exception) {
            Status.Code code = exception.getStatus().getCode();
            boolean retryable = retryPolicy.evaluate(RetryContext.builder()
                .clientName(request.getClientName()).protocol(ExchangeProtocol.GRPC)
                .grpcStatus(code).exception(exception).idempotent(request.isIdempotent())
                .build()).retry() && !logicalDeadlineExpired();
            throw new OutboundGrpcException(
                request.getClientName(), request.getServiceName(), request.getMethodName(),
                code, safeTrailers(exception.getTrailers()),
                Math.max(0, attempt - 1), deadline, retryable,
                new SanitizedRemoteCauseException(exception));
        }
    }

    private boolean logicalDeadlineExpired() {
        io.grpc.Deadline deadline = Context.current().getDeadline();
        return deadline != null && deadline.isExpired();
    }

    @SuppressWarnings("unchecked")
    private <T> T fallbackOrThrow(
        GrpcCallRequest request, ClientProperties client, Instant started,
        int attempts, OutboundCallException failure
    ) {
        Duration duration = Duration.between(started, nowInstant());
        FallbackContext fallbackContext = FallbackContext.builder()
            .clientName(request.getClientName()).protocol(ExchangeProtocol.GRPC)
            .operation(operation(request)).transportMethod(request.getMethodName())
            .finalException(failure)
            .finalStatus(failure instanceof OutboundGrpcException grpc
                ? grpc.getStatus().name() : null)
            .retryCount(Math.max(0, attempts - 1)).duration(duration)
            .circuitBreakerState(resilience.circuitBreakerState(request.getClientName()))
            .requestMetadata(maskAttributes(request.getAuditAttributes()))
            .traceId(trace().getTraceId()).requestId(requestId())
            .responseType(request.getResponseType()).build();
        Optional<?> handler = fallbacks == null
            ? Optional.empty() : fallbacks.find(fallbackContext, request.getResponseType());
        if (handler.isPresent()) {
            try {
                T value = (T) ((com.company.platform.exchange.resilience.fallback.OutboundFallbackHandler<?>)
                    handler.get()).fallback(fallbackContext);
                publish(client, new OutboundCallCompletedEvent(
                    eventData(request, started, attempts, true, failure, grpcStatus(failure))));
                record(request, duration, attempts, true, null, grpcStatus(failure));
                logFinal(request, duration, attempts, true, null);
                return value;
            } catch (RuntimeException fallbackFailure) {
                OutboundFallbackException wrapped = new OutboundFallbackException(
                    request.getClientName(), fallbackFailure);
                publish(client, new OutboundCallFailedEvent(
                    eventData(request, started, attempts, true, wrapped, grpcStatus(failure))));
                record(request, duration, attempts, true, wrapped, grpcStatus(failure));
                throw wrapped;
            }
        }
        publish(client, new OutboundCallFailedEvent(
            eventData(request, started, attempts, false, failure, grpcStatus(failure))));
        record(request, duration, attempts, false, failure, grpcStatus(failure));
        logFinal(request, duration, attempts, false, failure);
        throw failure;
    }

    private OutboundCallEventData eventData(
        GrpcCallRequest request, Instant started, int attempts,
        boolean fallback, Throwable failure, Status.Code status
    ) {
        CurrentTraceContext trace = trace();
        return OutboundCallEventData.builder()
            .clientName(request.getClientName()).protocol(ExchangeProtocol.GRPC)
            .operation(operation(request)).grpcService(request.getServiceName())
            .grpcMethod(request.getMethodName())
            .startedAt(started.atOffset(ZoneOffset.UTC)).completedAt(now())
            .duration(Duration.between(started, nowInstant()))
            .success(failure == null || fallback)
            .grpcStatus(status == null ? null : status.name())
            .attemptCount(attempts).retryCount(Math.max(0, attempts - 1))
            .fallbackUsed(fallback)
            .circuitBreakerState(resilience.circuitBreakerState(request.getClientName()))
            .requestId(requestId()).traceId(trace.getTraceId()).spanId(trace.getSpanId())
            .sourceApplication(sourceApplication)
            .errorType(failure == null ? null : failure.getClass().getSimpleName())
            .errorCode(failure instanceof OutboundCallException outbound
                ? outbound.errorCode() : null)
            .errorMessage(failure == null ? null : failure.getMessage())
            .customAttributes(maskAttributes(request.getAuditAttributes())).build();
    }

    private void publish(ClientProperties client, OutboundCallEvent event) {
        if (events == null || !client.getAudit().isEnabled()) {
            return;
        }
        try {
            events.publish(event);
        } catch (RuntimeException exception) {
            if (client.getAudit().getFailureMode() == AuditFailureMode.FAIL_CLOSED) {
                throw exception;
            }
        }
    }

    private void record(
        GrpcCallRequest request, Duration duration, int attempts,
        boolean fallback, Throwable failure, Status.Code status
    ) {
        if (metrics != null) {
            metrics.record(request.getClientName(), ExchangeProtocol.GRPC,
                "grpc", failure == null ? "SUCCESS" : "FAILED",
                status == null ? "UNKNOWN" : status.name(),
                failure == null ? "none" : failure.getClass().getSimpleName(),
                fallback, duration, Math.max(0, attempts - 1));
        }
    }

    private void logStarted(ClientProperties client, GrpcCallRequest request) {
        boolean enabled = request.getLoggingEnabled() == null
            ? client.getLogging().isEnabled() : request.getLoggingEnabled();
        if (enabled) {
            LOG.info("outbound_started client={} protocol=GRPC operation={} metadata={}",
                request.getClientName(), operation(request),
                maskAttributes(request.getRequestMetadata()));
        }
    }

    private static void logFinal(
        GrpcCallRequest request, Duration duration, int attempts,
        boolean fallback, Throwable failure
    ) {
        LOG.info(
            "outbound_finished client={} protocol=GRPC operation={} outcome={} attempts={} fallback={} duration_ms={}",
            request.getClientName(), operation(request),
            failure == null ? "SUCCESS" : "FAILED", attempts, fallback, duration.toMillis());
    }

    private Map<String, Object> maskAttributes(Map<String, ?> attributes) {
        return masker == null ? Map.of() : masker.maskAttributes(attributes);
    }

    private CurrentTraceContext trace() {
        if (traceContext == null || traceContext.getCurrentContext() == null) {
            return CurrentTraceContext.empty();
        }
        return traceContext.getCurrentContext();
    }

    private String requestId() {
        return requestContext == null ? null : requestContext.getRequestId();
    }

    private Instant nowInstant() {
        return time == null ? Instant.now() : time.nowInstant();
    }

    private OffsetDateTime now() {
        return time == null ? OffsetDateTime.now(ZoneOffset.UTC) : time.now();
    }

    private static String operation(GrpcCallRequest request) {
        return request.getServiceName() + "/" + request.getMethodName();
    }

    private static Status.Code grpcStatus(Throwable failure) {
        return failure instanceof OutboundGrpcException grpc
            ? grpc.getStatus() : Status.Code.UNKNOWN;
    }

    private static Map<String, String> safeTrailers(Metadata trailers) {
        // Trailer metadata is untrusted and may contain credentials or PII.
        // The safe default is an allow-list; no application-defined trailer is exported.
        return Map.of();
    }

    @Override
    public void close() {
        deadlines.shutdownNow();
    }
}
