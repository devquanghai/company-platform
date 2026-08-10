package com.company.platform.queue.api.kafka;

import java.util.List;
import java.util.Objects;

public record DeferredKafkaBatch(
    String claimId,
    String ownerId,
    long fencingToken,
    int attempt,
    List<DeferredKafkaMessage> messages
) {
    public DeferredKafkaBatch {
        Objects.requireNonNull(claimId, "claimId");
        Objects.requireNonNull(ownerId, "ownerId");
        messages = List.copyOf(messages);
        if (messages.isEmpty()) {
            throw new IllegalArgumentException("deferred Kafka batch must not be empty");
        }
    }
}
