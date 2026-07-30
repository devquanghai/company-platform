package com.company.platform.queue.observability.event;

import com.company.platform.queue.domain.model.QueueProviderType;

import java.time.Duration;
import java.time.Instant;

public record QueueAuditEvent(
    String eventId,
    String eventName,
    QueueProviderType provider,
    String broker,
    String destination,
    String messageId,
    String eventType,
    int schemaVersion,
    String outcome,
    int attemptCount,
    Duration duration,
    String correlationId,
    String traceId,
    String errorCode,
    Instant occurredAt
) {
}
