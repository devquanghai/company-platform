package com.company.platform.queue.api.consume;

import com.company.platform.queue.domain.model.QueueProviderType;

import java.time.Instant;
import java.util.Map;

public record MessageContext(
    QueueProviderType provider,
    String broker,
    String subscription,
    String destination,
    String physicalDestination,
    String messageId,
    String correlationId,
    String causationId,
    Map<String, String> headers,
    Instant receivedAt,
    int deliveryAttempt,
    Integer partition,
    Long offset,
    String consumerGroup,
    String exchange,
    String routingKey,
    boolean redelivered,
    String traceId
) {
    public MessageContext {
        headers = Map.copyOf(headers == null ? Map.of() : headers);
    }
}
