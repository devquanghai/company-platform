package com.company.platform.exchange.client.internal.application;

import com.company.platform.core.context.RequestContextProvider;
import com.company.platform.core.time.TimeProvider;
import com.company.platform.core.trace.CurrentTraceContext;
import com.company.platform.core.trace.TraceContextProvider;
import com.company.platform.exchange.api.exception.ServiceExchangeClientException;
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
import com.company.platform.exchange.domain.model.ExchangeProtocol;
import com.company.platform.exchange.resilience.executor.ResilienceExecutor;
import com.company.platform.exchange.resilience.fallback.FallbackContext;
import com.company.platform.exchange.resilience.fallback.OutboundFallbackRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

public final class ClientCallLifecycle {
    private static final Logger LOG = LoggerFactory.getLogger("OUTBOUND_CALL");

    private final String clientName;
    private final ClientProperties client;
    private final OutboundFallbackRegistry fallbacks;
    private final OutboundCallEventPublisher events;
    private final ResilienceExecutor resilience;
    private final TimeProvider time;
    private final RequestContextProvider requestContext;
    private final TraceContextProvider traceContext;
    private final String sourceApplication;

    public ClientCallLifecycle(
        String clientName, ClientProperties client,
        OutboundFallbackRegistry fallbacks, OutboundCallEventPublisher events,
        ResilienceExecutor resilience, TimeProvider time,
        RequestContextProvider requestContext, TraceContextProvider traceContext,
        String sourceApplication
    ) {
        this.clientName = clientName;
        this.client = client;
        this.fallbacks = fallbacks;
        this.events = events;
        this.resilience = resilience;
        this.time = time;
        this.requestContext = requestContext;
        this.traceContext = traceContext;
        this.sourceApplication = sourceApplication;
    }

    public CallState start(String method) {
        CallState state = new CallState(method, time.nowInstant());
        publish(new OutboundCallStartedEvent(data(state, false, false, null)));
        if (client.getLogging().isEnabled()) {
            LOG.info("outbound_started client={} protocol=HTTP operation={}",
                clientName, method);
        }
        return state;
    }

    public void success(CallState state) {
        publish(new OutboundCallCompletedEvent(data(state, true, false, null)));
        logFinal(state, "SUCCESS", false);
    }

    public <T> T failure(
        CallState state, Class<T> responseType, RuntimeException failure
    ) {
        FallbackContext context = FallbackContext.builder()
            .clientName(clientName).protocol(ExchangeProtocol.HTTP)
            .operation(state.method()).transportMethod(state.method())
            .finalException(failure).finalStatus(status(failure))
            .duration(duration(state)).retryCount(0)
            .circuitBreakerState(resilience.circuitBreakerState(clientName))
            .traceId(trace().getTraceId()).requestId(requestContext.getRequestId())
            .responseType(responseType).build();
        var fallback = fallbacks.find(context, responseType);
        if (fallback.isPresent()) {
            try {
                T value = fallback.get().fallback(context);
                publish(new OutboundCallCompletedEvent(data(state, true, true, failure)));
                logFinal(state, "FALLBACK", true);
                return value;
            } catch (RuntimeException fallbackFailure) {
                OutboundFallbackException wrapped = new OutboundFallbackException(
                    clientName, fallbackFailure);
                publish(new OutboundCallFailedEvent(data(state, false, true, wrapped)));
                logFinal(state, "FALLBACK_FAILED", true);
                throw wrapped;
            }
        }
        publish(new OutboundCallFailedEvent(data(state, false, false, failure)));
        logFinal(state, "FAILED", false);
        throw failure;
    }

    private OutboundCallEventData data(
        CallState state, boolean success, boolean fallback, Throwable failure
    ) {
        CurrentTraceContext trace = trace();
        return OutboundCallEventData.builder()
            .clientName(clientName).protocol(ExchangeProtocol.HTTP)
            .operation(state.method()).httpMethod(state.method())
            .startedAt(state.started().atOffset(ZoneOffset.UTC))
            .completedAt(time.now()).duration(duration(state)).success(success)
            .httpStatus(failure instanceof ServiceExchangeClientException http
                ? http.status() : null)
            .attemptCount(1).retryCount(0).fallbackUsed(fallback)
            .circuitBreakerState(resilience.circuitBreakerState(clientName))
            .requestId(requestContext.getRequestId()).traceId(trace.getTraceId())
            .spanId(trace.getSpanId()).sourceApplication(sourceApplication)
            .errorType(failure == null ? null : failure.getClass().getSimpleName())
            .errorCode(failure instanceof OutboundCallException outbound
                ? outbound.errorCode() : null)
            .build();
    }

    private void publish(OutboundCallEvent event) {
        if (!client.getAudit().isEnabled()) {
            return;
        }
        try {
            events.publish(event);
        } catch (RuntimeException failure) {
            if (client.getAudit().getFailureMode() == AuditFailureMode.FAIL_CLOSED) {
                throw failure;
            }
        }
    }

    private void logFinal(CallState state, String outcome, boolean fallback) {
        if (client.getLogging().isEnabled()) {
            LOG.info("outbound_finished client={} protocol=HTTP operation={} outcome={} fallback={} duration_ms={}",
                clientName, state.method(), outcome, fallback, duration(state).toMillis());
        }
    }

    private Duration duration(CallState state) {
        return Duration.between(state.started(), time.nowInstant());
    }

    private String status(Throwable failure) {
        return failure instanceof ServiceExchangeClientException http
            ? String.valueOf(http.status()) : null;
    }

    private CurrentTraceContext trace() {
        CurrentTraceContext value = traceContext.getCurrentContext();
        return value == null ? CurrentTraceContext.empty() : value;
    }

    public record CallState(String method, Instant started) { }
}
