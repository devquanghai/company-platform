package com.company.platform.queue.api.model;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

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
    public MessageMetadata {
        messageId = requireText(messageId, "messageId");
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
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
