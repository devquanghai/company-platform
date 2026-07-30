package com.company.platform.queue.api.publish;

import com.company.platform.queue.domain.model.QueueProviderType;
import com.company.platform.queue.domain.result.PublishStatus;

import java.time.Duration;
import java.time.Instant;

public record PublishResult(
    PublishStatus status,
    QueueProviderType provider,
    String broker,
    String destination,
    String physicalDestination,
    String messageId,
    Integer partition,
    Long offset,
    String routingKey,
    boolean confirmed,
    boolean returned,
    int attemptCount,
    Duration duration,
    Instant publishedAt,
    String traceId,
    String failureCode
) {
}
