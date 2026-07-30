package com.company.platform.queue.reliability.outbox;

import java.time.Instant;
import java.util.Map;

public record OutboxRecord(
    String id,
    String aggregateType,
    String aggregateId,
    String destination,
    String messageKey,
    String messageId,
    String eventType,
    int schemaVersion,
    byte[] payload,
    Map<String, String> headers,
    Instant createdAt,
    Instant availableAt,
    Instant publishedAt,
    OutboxStatus status,
    int attemptCount,
    String lastErrorCode,
    String ownerId,
    long fencingToken,
    Instant lockedUntil
) {
    public OutboxRecord {
        payload = payload.clone();
        headers = Map.copyOf(headers == null ? Map.of() : headers);
    }

    @Override
    public byte[] payload() {
        return payload.clone();
    }
}
