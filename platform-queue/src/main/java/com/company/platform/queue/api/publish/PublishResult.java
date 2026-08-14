package com.company.platform.queue.api.publish;

import com.company.platform.queue.api.model.QueueProviderType;
import com.company.platform.queue.api.model.PublishStatus;

import java.time.Instant;

public record PublishResult(
    PublishStatus status,
    QueueProviderType provider,
    String destination,
    String messageId,
    Instant publishedAt,
    String failureCode
) {
}
