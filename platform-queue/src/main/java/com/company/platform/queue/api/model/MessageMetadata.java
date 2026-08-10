package com.company.platform.queue.api.model;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.nio.charset.StandardCharsets;

public record MessageMetadata(
    String messageId,
    String eventId,
    String correlationId,
    String causationId,
    String traceId,
    String spanId,
    String sourceApplication,
    String destination,
    String eventType,
    int schemaVersion,
    Instant occurredAt,
    Instant publishedAt,
    String contentType,
    Map<String, String> headers
) {
    private static final int MAX_FIELD_BYTES = 8 * 1024;
    private static final int MAX_METADATA_BYTES = 32 * 1024;

    public MessageMetadata {
        messageId = requireText(messageId, "messageId");
        eventId = optionalText(eventId, "eventId");
        correlationId = optionalText(correlationId, "correlationId");
        causationId = optionalText(causationId, "causationId");
        traceId = optionalText(traceId, "traceId");
        spanId = optionalText(spanId, "spanId");
        sourceApplication = requireText(sourceApplication, "sourceApplication");
        destination = requireText(destination, "destination");
        eventType = requireText(eventType, "eventType");
        contentType = requireText(contentType, "contentType");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(publishedAt, "publishedAt");
        if (schemaVersion < 1) {
            throw new IllegalArgumentException("schemaVersion must be at least 1");
        }
        headers = Map.copyOf(headers == null ? Map.of() : headers);
        int totalBytes = bytes(
            messageId, eventId, correlationId, causationId, traceId, spanId,
            sourceApplication, destination, eventType, contentType);
        for (Map.Entry<String, String> header : headers.entrySet()) {
            String name = requireText(header.getKey(), "header name");
            String value = requireText(header.getValue(), "header value");
            totalBytes = Math.addExact(totalBytes, bytes(name, value));
        }
        if (totalBytes > MAX_METADATA_BYTES) {
            throw new IllegalArgumentException("message metadata exceeds size limit");
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        validate(value, field);
        return value;
    }

    private static String optionalText(String value, String field) {
        if (value != null) {
            validate(value, field);
        }
        return value;
    }

    private static void validate(String value, String field) {
        if (value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(field + " contains control characters");
        }
        if (value.getBytes(StandardCharsets.UTF_8).length > MAX_FIELD_BYTES) {
            throw new IllegalArgumentException(field + " exceeds size limit");
        }
    }

    private static int bytes(String... values) {
        int total = 0;
        for (String value : values) {
            if (value != null) {
                total = Math.addExact(
                    total, value.getBytes(StandardCharsets.UTF_8).length);
            }
        }
        return total;
    }
}
