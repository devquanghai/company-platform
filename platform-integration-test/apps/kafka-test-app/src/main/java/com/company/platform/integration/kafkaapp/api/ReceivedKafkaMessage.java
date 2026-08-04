package com.company.platform.integration.kafkaapp.api;

import com.company.platform.integration.kafkaapp.model.KafkaTestEvent;

import java.time.Instant;

public record ReceivedKafkaMessage(
    KafkaTestEvent event,
    String messageId,
    String correlationId,
    String topic,
    Integer partition,
    Long offset,
    int deliveryAttempt,
    Instant receivedAt
) {
}
