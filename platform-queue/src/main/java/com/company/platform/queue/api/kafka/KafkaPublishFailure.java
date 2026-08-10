package com.company.platform.queue.api.kafka;

import com.company.platform.queue.domain.result.PublishStatus;

import java.util.Objects;

public record KafkaPublishFailure(
    PublishStatus status,
    String broker,
    String destination,
    String topic,
    String messageId,
    String correlationId,
    String traceId,
    String failureCode,
    String exceptionType
) {
    public KafkaPublishFailure {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(failureCode, "failureCode");
        Objects.requireNonNull(exceptionType, "exceptionType");
    }
}
