package com.company.platform.integration.queue.internal.domain;

import java.time.Instant;
import java.util.Map;

public record QueueDemoEvent(
    String eventId,
    QueueMode mode,
    String businessKey,
    String message,
    Map<String, String> attributes,
    Instant createdAt
) {
    public QueueDemoEvent {
        attributes = Map.copyOf(attributes == null ? Map.of() : attributes);
    }
}
