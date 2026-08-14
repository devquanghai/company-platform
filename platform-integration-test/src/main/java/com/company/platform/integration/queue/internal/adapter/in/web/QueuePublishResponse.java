package com.company.platform.integration.queue.internal.adapter.in.web;

import com.company.platform.queue.api.publish.PublishResult;

import java.time.Instant;

public record QueuePublishResponse(
    String mode,
    String eventId,
    String status,
    String destination,
    String messageId,
    Instant publishedAt
) {
    static QueuePublishResponse from(
        String mode, String eventId, PublishResult result
    ) {
        return new QueuePublishResponse(
            mode, eventId, result.status().name(), result.destination(),
            result.messageId(), result.publishedAt());
    }
}
