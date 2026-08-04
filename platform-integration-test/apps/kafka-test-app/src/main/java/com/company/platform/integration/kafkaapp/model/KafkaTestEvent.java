package com.company.platform.integration.kafkaapp.model;

import java.time.Instant;

public record KafkaTestEvent(
    String eventId,
    String aggregateId,
    String message,
    Instant createdAt
) {
}
