package com.company.platform.queue.api.model;

import java.util.Objects;

public record MessageEnvelope<T>(MessageMetadata metadata, T payload) {
    public MessageEnvelope {
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(payload, "payload");
    }
}
