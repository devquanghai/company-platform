package com.company.platform.integration.kafkaapp.api;

import com.company.platform.queue.api.publish.PublishResult;

import java.time.Instant;

public record KafkaPublishResponse(
    String status,
    String messageId,
    String topic,
    Integer partition,
    Long offset,
    boolean confirmed,
    int attemptCount,
    Instant publishedAt,
    String failureCode
) {
    public static KafkaPublishResponse from(PublishResult result) {
        return new KafkaPublishResponse(
            result.status().name(),
            result.messageId(),
            result.physicalDestination(),
            result.partition(),
            result.offset(),
            result.confirmed(),
            result.attemptCount(),
            result.publishedAt(),
            result.failureCode());
    }
}
