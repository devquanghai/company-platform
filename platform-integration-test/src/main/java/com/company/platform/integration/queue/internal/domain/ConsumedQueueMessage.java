package com.company.platform.integration.queue.internal.domain;

import java.time.Instant;

public record ConsumedQueueMessage(
    QueueDemoEvent event,
    String messageId,
    String subscription,
    String topic,
    Integer partition,
    Long offset,
    int deliveryAttempt,
    Instant receivedAt
) { }
