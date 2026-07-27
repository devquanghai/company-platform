package com.company.platform.core.rest.factory;

import com.company.platform.core.context.RequestContextProvider;
import com.company.platform.core.rest.response.ResponseMetadata;
import com.company.platform.core.time.TimeProvider;
import com.company.platform.core.trace.CurrentTraceContext;
import com.company.platform.core.trace.TraceContextProvider;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class ResponseMetadataFactory {

    private final RequestContextProvider requestContextProvider;
    private final TraceContextProvider traceContextProvider;
    private final TimeProvider timeProvider;

    public ResponseMetadataFactory(
        RequestContextProvider requestContextProvider,
        TraceContextProvider traceContextProvider,
        TimeProvider timeProvider
    ) {
        this.requestContextProvider = Objects.requireNonNull(
            requestContextProvider,
            "requestContextProvider must not be null"
        );
        this.traceContextProvider = Objects.requireNonNull(
            traceContextProvider,
            "traceContextProvider must not be null"
        );
        this.timeProvider = Objects.requireNonNull(
            timeProvider,
            "timeProvider must not be null"
        );
    }

    public ResponseMetadata create() {
        return create(Map.of());
    }

    public ResponseMetadata create(
        Map<String, Object> attributes
    ) {
        return create(null, null, attributes);
    }

    public ResponseMetadata create(String url, String method) {
        return create(url, method, Map.of());
    }

    public ResponseMetadata create(
        String url,
        String method,
        Map<String, Object> attributes
    ) {
        CurrentTraceContext currentTraceContext =
            traceContextProvider.getCurrentContext();

        String traceId = currentTraceContext.getTraceId();
        String spanId = currentTraceContext.getSpanId();

        String requestId = firstNonBlank(
            UUID.randomUUID().toString(),
            requestContextProvider.getRequestId(),
            traceId
        );

        String correlationId = firstNonBlank(
            requestId,
            requestContextProvider.getCorrelationId(),
            requestId
        );

        return new ResponseMetadata(
            firstNonBlank(requestContextProvider.getRequestUrl(), url),
            firstNonBlank(requestContextProvider.getRequestMethod(), method),
            requestId,
            correlationId,
            traceId,
            spanId,
            timeProvider.now(),
            attributes
        );
    }

    private static String firstNonBlank(
        String fallback,
        String... values
    ) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return fallback;
    }
}
