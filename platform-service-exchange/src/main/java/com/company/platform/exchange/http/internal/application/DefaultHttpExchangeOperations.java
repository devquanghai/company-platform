package com.company.platform.exchange.http.internal.application;

import com.company.platform.exchange.client.internal.application.ClientConfigurationResolver;
import com.company.platform.core.context.RequestContextProvider;
import com.company.platform.core.time.TimeProvider;
import com.company.platform.core.trace.CurrentTraceContext;
import com.company.platform.core.trace.TraceContextProvider;
import com.company.platform.exchange.http.internal.adapter.SecureUriResolver;
import com.company.platform.exchange.api.http.ExchangeRequest;
import com.company.platform.exchange.api.http.ExchangeResponse;
import com.company.platform.exchange.api.http.HttpClientRegistry;
import com.company.platform.exchange.api.http.HttpExchangeOperations;
import com.company.platform.exchange.api.http.OutboundCallMetadata;
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
import com.company.platform.exchange.domain.exception.OutboundHttpException;
import com.company.platform.exchange.domain.exception.SanitizedRemoteCauseException;
import com.company.platform.exchange.domain.model.ExchangeProtocol;
import com.company.platform.exchange.domain.policy.RetryContext;
import com.company.platform.exchange.domain.policy.RetryDecision;
import com.company.platform.exchange.domain.policy.RetryDecisionPolicy;
import com.company.platform.exchange.observability.logging.OutboundDataMasker;
import com.company.platform.exchange.observability.logging.CurlGenerator;
import com.company.platform.exchange.observability.metrics.ExchangeMetrics;
import com.company.platform.exchange.resilience.executor.ResilienceExecutionContext;
import com.company.platform.exchange.resilience.executor.ResilienceExecutor;
import com.company.platform.exchange.resilience.fallback.FallbackContext;
import com.company.platform.exchange.resilience.fallback.OutboundFallbackRegistry;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Type;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public final class DefaultHttpExchangeOperations implements HttpExchangeOperations {

    private static final Logger CALL_LOG = LoggerFactory.getLogger("OUTBOUND_CALL");
    private static final Logger CURL_LOG = LoggerFactory.getLogger("OUTBOUND_CURL");
    private final HttpClientRegistry clients;
    private final ClientConfigurationResolver configurations;
    private final SecureUriResolver uriResolver;
    private final RetryDecisionPolicy retryPolicy;
    private final ResilienceExecutor resilience;
    private final OutboundFallbackRegistry fallbacks;
    private final OutboundCallEventPublisher events;
    private final OutboundDataMasker masker;
    private final TimeProvider time;
    private final RequestContextProvider requestContext;
    private final TraceContextProvider traceContext;
    private final CurlGenerator curl;
    private final ExchangeMetrics metrics;
    private final String sourceApplication;

    public DefaultHttpExchangeOperations(
        HttpClientRegistry clients, ClientConfigurationResolver configurations,
        SecureUriResolver uriResolver, RetryDecisionPolicy retryPolicy,
        ResilienceExecutor resilience, OutboundFallbackRegistry fallbacks,
        OutboundCallEventPublisher events, OutboundDataMasker masker,
        TimeProvider time, RequestContextProvider requestContext,
        TraceContextProvider traceContext
    ) {
        this(clients, configurations, uriResolver, retryPolicy, resilience, fallbacks,
            events, masker, time, requestContext, traceContext, null, null, "unknown");
    }

    public DefaultHttpExchangeOperations(
        HttpClientRegistry clients, ClientConfigurationResolver configurations,
        SecureUriResolver uriResolver, RetryDecisionPolicy retryPolicy,
        ResilienceExecutor resilience, OutboundFallbackRegistry fallbacks,
        OutboundCallEventPublisher events, OutboundDataMasker masker,
        TimeProvider time, RequestContextProvider requestContext,
        TraceContextProvider traceContext, CurlGenerator curl,
        ExchangeMetrics metrics, String sourceApplication
    ) {
        this.clients = clients;
        this.configurations = configurations;
        this.uriResolver = uriResolver;
        this.retryPolicy = retryPolicy;
        this.resilience = resilience;
        this.fallbacks = fallbacks;
        this.events = events;
        this.masker = masker;
        this.time = time;
        this.requestContext = requestContext;
        this.traceContext = traceContext;
        this.curl = curl;
        this.metrics = metrics;
        this.sourceApplication = sourceApplication;
    }

    @Override
    public <T> ExchangeResponse<T> get(String clientName, String path, Class<T> type) {
        return exchange(request(clientName, HttpMethod.GET, path, null),
            ParameterizedTypeReference.forType(type));
    }

    @Override
    public <T> ExchangeResponse<T> get(
        String clientName, String path, Map<String, ?> queryParams,
        HttpHeaders headers, ParameterizedTypeReference<T> type
    ) {
        return exchange(ExchangeRequest.builder().clientName(clientName)
            .method(HttpMethod.GET).path(path).queryParameters(queryParams)
            .headers(headers).idempotent(true).build(), type);
    }

    @Override
    public <T> ExchangeResponse<T> post(
        String clientName, String path, Object body, Class<T> type
    ) {
        return exchange(request(clientName, HttpMethod.POST, path, body),
            ParameterizedTypeReference.forType(type));
    }

    @Override
    public <T> ExchangeResponse<T> put(
        String clientName, String path, Object body, Class<T> type
    ) {
        return exchange(request(clientName, HttpMethod.PUT, path, body),
            ParameterizedTypeReference.forType(type));
    }

    @Override
    public <T> ExchangeResponse<T> patch(
        String clientName, String path, Object body, Class<T> type
    ) {
        return exchange(request(clientName, HttpMethod.PATCH, path, body),
            ParameterizedTypeReference.forType(type));
    }

    @Override
    public <T> ExchangeResponse<T> delete(String clientName, String path, Class<T> type) {
        return exchange(request(clientName, HttpMethod.DELETE, path, null),
            ParameterizedTypeReference.forType(type));
    }

    @Override
    public <T> ExchangeResponse<T> exchange(
        ExchangeRequest request, ParameterizedTypeReference<T> responseType
    ) {
        ClientProperties configuration = configurations.resolve(
            request.getClientName(), ExchangeProtocol.HTTP);
        URI target = uriResolver.resolve(request, configuration.getBaseUrl());
        Instant started = time.nowInstant();
        AtomicInteger attempts = new AtomicInteger();
        logRequest(configuration, request, target);
        publish(configuration, new OutboundCallStartedEvent(
            eventData(request, target, started, null, attempts.get(), false, null)));
        try {
            ResponseEntity<T> entity = configuration.isResilienceEnabled()
                && !Boolean.FALSE.equals(request.getResilienceEnabled())
                ? resilience.execute(
                ResilienceExecutionContext.builder()
                    .clientName(request.getClientName())
                    .operation(request.getMethod() + " " + request.getPath()).build(),
                () -> invoke(request, responseType, configuration, target, started, attempts))
                : invoke(request, responseType, configuration, target, started, attempts);
            Duration duration = Duration.between(started, time.nowInstant());
            ExchangeResponse<T> response = response(request, entity, duration, attempts.get(), false);
            publish(configuration, new OutboundCallCompletedEvent(
                eventData(request, target, started, entity.getStatusCode().value(),
                    attempts.get(), false, null)));
            record(request, duration, attempts.get(), false, null,
                Integer.toString(entity.getStatusCode().value()));
            logFinal(request, duration, attempts.get(), false, null);
            return response;
        } catch (OutboundCallException failure) {
            return fallbackOrThrow(
                request, responseType, configuration, target, started, attempts.get(), failure);
        }
    }

    private <T> ResponseEntity<T> invoke(
        ExchangeRequest request, ParameterizedTypeReference<T> responseType,
        ClientProperties configuration, URI target, Instant started, AtomicInteger attempts
    ) {
        attempts.incrementAndGet();
        if (configuration.getAudit().isPublishAttemptEvents()) {
            publish(configuration, new com.company.platform.exchange.audit.event.OutboundCallAttemptEvent(
                eventData(request, target, started, null, attempts.get(), false, null)));
        }
        try {
            RestClient.RequestBodySpec spec = clients.getClient(request.getClientName())
                .method(request.getMethod()).uri(target)
                .headers(headers -> headers.addAll(request.getHeaders()));
            if (request.getContentType() != null) {
                spec.contentType(request.getContentType());
            }
            if (!request.getAccept().isEmpty()) {
                spec.accept(request.getAccept().toArray(org.springframework.http.MediaType[]::new));
            }
            if (request.getIdempotencyKey() != null) {
                spec.header("Idempotency-Key", request.getIdempotencyKey());
            }
            request.getCookies().forEach((name, value) -> spec.header(
                HttpHeaders.COOKIE, name + "=" + value));
            if (request.getBody() != null) {
                spec.body(request.getBody());
            }
            return spec.retrieve().toEntity(responseType);
        } catch (RestClientResponseException exception) {
            RetryDecision decision = retryPolicy.evaluate(RetryContext.builder()
                .clientName(request.getClientName()).protocol(ExchangeProtocol.HTTP)
                .httpMethod(request.getMethod()).httpStatus(exception.getStatusCode().value())
                .exception(exception).idempotent(request.isIdempotent())
                .idempotencyKey(request.getIdempotencyKey()).build());
            throw httpFailure(request, target, started, attempts.get(), exception, decision.retry());
        } catch (OutboundCallException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            RetryDecision decision = retryPolicy.evaluate(RetryContext.builder()
                .clientName(request.getClientName()).protocol(ExchangeProtocol.HTTP)
                .httpMethod(request.getMethod()).exception(exception)
                .idempotent(request.isIdempotent())
                .idempotencyKey(request.getIdempotencyKey()).build());
            throw httpFailure(request, target, started, attempts.get(), exception, decision.retry());
        }
    }

    private OutboundHttpException httpFailure(
        ExchangeRequest request, URI target, Instant started, int attempts,
        RuntimeException exception, boolean retryable
    ) {
        RestClientResponseException response = exception instanceof RestClientResponseException value
            ? value : null;
        ClientProperties client = configurations.resolve(
            request.getClientName(), ExchangeProtocol.HTTP);
        HttpHeaders safeHeaders = response == null || response.getResponseHeaders() == null
            || !client.getLogging().isResponseHeadersEnabled()
            ? HttpHeaders.EMPTY : masker.maskHeaders(response.getResponseHeaders());
        String safeBody = response == null || !client.getLogging().isResponseBodyEnabled()
            ? null : masker.maskBody(response.getResponseBodyAsString(),
                client.getLogging().getMaxBodyLength());
        return new OutboundHttpException(
            request.getClientName(), request.getMethod().name(), masker.maskUri(target),
            response == null ? null : response.getStatusCode().value(),
            copyHeaders(safeHeaders),
            safeBody, Math.max(0, attempts - 1),
            Duration.between(started, time.nowInstant()), retryable,
            response == null ? transportFailure(exception)
                : response.getStatusCode().value() == 408
                    || response.getStatusCode().is5xxServerError(),
            response == null ? new SanitizedRemoteCauseException(exception) : null);
    }

    private boolean transportFailure(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof java.io.IOException
                || current instanceof java.util.concurrent.TimeoutException
                || current instanceof org.springframework.web.client.ResourceAccessException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private <T> ExchangeResponse<T> fallbackOrThrow(
        ExchangeRequest request, ParameterizedTypeReference<T> responseType,
        ClientProperties configuration, URI target, Instant started,
        int attempts, OutboundCallException failure
    ) {
        Duration duration = Duration.between(started, time.nowInstant());
        Class<T> responseClass = responseClass(responseType.getType());
        FallbackContext context = FallbackContext.builder()
            .clientName(request.getClientName()).protocol(ExchangeProtocol.HTTP)
            .operation(request.getMethod() + " " + request.getPath())
            .transportMethod(request.getMethod().name()).finalException(failure)
            .finalStatus(failure instanceof OutboundHttpException http
                ? String.valueOf(http.getStatus()) : null)
            .retryCount(Math.max(0, attempts - 1)).duration(duration)
            .circuitBreakerState(resilience.circuitBreakerState(request.getClientName()))
            .requestMetadata(masker.maskAttributes(request.getAuditAttributes()))
            .traceId(trace().getTraceId()).requestId(requestContext.getRequestId())
            .responseType(responseClass).build();
        var fallback = fallbacks.find(context, responseClass);
        if (fallback.isPresent()) {
            try {
                T body = fallback.get().fallback(context);
                ExchangeResponse<T> response = new ExchangeResponse<>(
                    200, HttpHeaders.EMPTY, body, duration,
                    metadata(request.getClientName(), attempts, true));
                publish(configuration, new OutboundCallCompletedEvent(
                    eventData(request, target, started, null, attempts, true, failure)));
                record(request, duration, attempts, true, null, "FALLBACK");
                logFinal(request, duration, attempts, true, null);
                return response;
            } catch (RuntimeException fallbackFailure) {
                OutboundFallbackException wrapped = new OutboundFallbackException(
                    request.getClientName(), fallbackFailure);
                publish(configuration, new OutboundCallFailedEvent(
                    eventData(request, target, started, null, attempts, true, wrapped)));
                record(request, duration, attempts, true, wrapped, "FALLBACK_FAILED");
                logFinal(request, duration, attempts, true, wrapped);
                throw wrapped;
            }
        }
        publish(configuration, new OutboundCallFailedEvent(
            eventData(request, target, started, null, attempts, false, failure)));
        record(request, duration, attempts, false, failure, "FAILED");
        logFinal(request, duration, attempts, false, failure);
        throw failure;
    }

    private <T> ExchangeResponse<T> response(
        ExchangeRequest request, ResponseEntity<T> entity, Duration duration,
        int attempts, boolean fallback
    ) {
        return new ExchangeResponse<>(entity.getStatusCode().value(), entity.getHeaders(),
            entity.getBody(), duration, metadata(request.getClientName(), attempts, fallback));
    }

    private OutboundCallMetadata metadata(String client, int attempts, boolean fallback) {
        CurrentTraceContext trace = trace();
        return OutboundCallMetadata.builder().clientName(client)
            .requestId(requestContext.getRequestId())
            .correlationId(requestContext.getCorrelationId())
            .traceId(trace.getTraceId()).spanId(trace.getSpanId())
            .attemptCount(attempts).retryCount(Math.max(0, attempts - 1))
            .fallbackUsed(fallback).timestamp(time.now()).build();
    }

    private OutboundCallEventData eventData(
        ExchangeRequest request, URI target, Instant started, Integer status,
        int attempts, boolean fallback, Throwable failure
    ) {
        CurrentTraceContext trace = trace();
        return OutboundCallEventData.builder()
            .clientName(request.getClientName()).protocol(ExchangeProtocol.HTTP)
            .operation(request.getMethod() + " " + safePath(request.getPath()))
            .httpMethod(request.getMethod().name()).target(masker.maskUri(target).toString())
            .startedAt(started.atOffset(java.time.ZoneOffset.UTC))
            .completedAt(time.now()).duration(Duration.between(started, time.nowInstant()))
            .success(failure == null || fallback).httpStatus(status)
            .attemptCount(attempts).retryCount(Math.max(0, attempts - 1))
            .fallbackUsed(fallback)
            .circuitBreakerState(resilience.circuitBreakerState(request.getClientName()))
            .requestId(requestContext.getRequestId()).traceId(trace.getTraceId())
            .spanId(trace.getSpanId()).sourceApplication(sourceApplication)
            .errorType(failure == null ? null : failure.getClass().getSimpleName())
            .errorCode(failure instanceof OutboundCallException outbound
                ? outbound.errorCode() : null)
            .errorMessage(failure == null ? null : failure.getMessage())
            .customAttributes(masker.maskAttributes(request.getAuditAttributes())).build();
    }

    private void publish(ClientProperties client, OutboundCallEvent event) {
        if (!client.getAudit().isEnabled()) {
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

    private CurrentTraceContext trace() {
        CurrentTraceContext value = traceContext.getCurrentContext();
        return value == null ? CurrentTraceContext.empty() : value;
    }

    private void logRequest(
        ClientProperties client, ExchangeRequest request, URI target
    ) {
        boolean enabled = request.getLoggingEnabled() == null
            ? client.getLogging().isEnabled() : request.getLoggingEnabled();
        if (!enabled) {
            return;
        }
        CALL_LOG.info("outbound_started client={} protocol=HTTP operation={} target={}",
            request.getClientName(), request.getMethod() + " " + safePath(request.getPath()),
            masker.maskUri(target));
        if (client.getLogging().isCurlEnabled() && curl != null) {
            CURL_LOG.info("outbound_curl client={} command={}", request.getClientName(),
                curl.generate(request, target, client.getLogging().getMaxBodyLength(),
                    client.getLogging().isRequestHeadersEnabled(),
                    client.getLogging().isRequestBodyEnabled()));
        }
    }

    private void logFinal(
        ExchangeRequest request, Duration duration, int attempts,
        boolean fallback, Throwable failure
    ) {
        CALL_LOG.info(
            "outbound_finished client={} protocol=HTTP operation={} outcome={} attempts={} fallback={} duration_ms={}",
            request.getClientName(), request.getMethod(),
            failure == null ? "SUCCESS" : "FAILED", attempts, fallback, duration.toMillis());
    }

    private void record(
        ExchangeRequest request, Duration duration, int attempts,
        boolean fallback, Throwable failure, String status
    ) {
        if (metrics != null) {
            metrics.record(request.getClientName(), ExchangeProtocol.HTTP,
                request.getMethod().name(), failure == null ? "SUCCESS" : "FAILED",
                status, failure == null ? "none" : failure.getClass().getSimpleName(),
                fallback, duration, Math.max(0, attempts - 1));
        }
    }

    private static String safePath(String configuredPath) {
        if (configuredPath == null || configuredPath.isBlank()
            || "/".equals(configuredPath)) {
            return "/";
        }
        return "/[redacted]";
    }

    private static Map<String, List<String>> copyHeaders(HttpHeaders headers) {
        Map<String, List<String>> result = new java.util.LinkedHashMap<>();
        headers.forEach((name, values) -> result.put(name, List.copyOf(values)));
        return Map.copyOf(result);
    }

    @SuppressWarnings("unchecked")
    private static <T> Class<T> responseClass(Type type) {
        return (Class<T>) (type instanceof Class<?> value ? value : Object.class);
    }

    private static ExchangeRequest request(
        String client, HttpMethod method, String path, Object body
    ) {
        return ExchangeRequest.builder().clientName(client).method(method).path(path)
            .body(body).idempotent(method == HttpMethod.GET || method == HttpMethod.HEAD
                || method == HttpMethod.OPTIONS || method == HttpMethod.PUT
                || method == HttpMethod.DELETE).build();
    }
}
