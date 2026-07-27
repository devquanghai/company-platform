package com.company.platform.core.rest.response;

import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.FieldDefaults;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Objects;

@Getter
@ToString
@EqualsAndHashCode
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public final class ResponseMetadata {
    String url;
    String method;
    String requestId;
    String correlationId;
    String traceId;
    String spanId;
    OffsetDateTime timestamp;
    Map<String, Object> attributes;

    public ResponseMetadata(
        String url,
        String method,
        String requestId,
        String correlationId,
        String traceId,
        String spanId,
        OffsetDateTime timestamp,
        Map<String, Object> attributes
    ) {
        this.url = normalize(url);
        this.method = normalize(method);
        this.requestId = normalize(requestId);
        this.correlationId = normalize(correlationId);
        this.traceId = normalize(traceId);
        this.spanId = normalize(spanId);
        this.timestamp = Objects.requireNonNull(
            timestamp,
            "timestamp must not be null"
        );
        this.attributes = Map.copyOf(
            attributes == null
                ? Map.of()
                : attributes
        );
    }

    public boolean hasTrace() {
        return traceId != null;
    }

    public static ResponseMetadata empty() {
        return new ResponseMetadata(null, null, null, null, null, null, OffsetDateTime.now(), Map.of());
    }

    private static String normalize(String value) {
        return value == null || value.isBlank()
            ? null
            : value;
    }
}
