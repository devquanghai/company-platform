package com.company.platform.queue.api.kafka;

import com.company.platform.queue.api.consume.MessageContext;

import java.util.Arrays;
import java.util.Objects;

public record DeferredKafkaMessage(
    String subscription,
    String messageKey,
    byte[] body,
    MessageContext context
) {
    public DeferredKafkaMessage {
        Objects.requireNonNull(subscription, "subscription");
        body = Arrays.copyOf(Objects.requireNonNull(body, "body"), body.length);
        Objects.requireNonNull(context, "context");
    }

    @Override
    public byte[] body() {
        return Arrays.copyOf(body, body.length);
    }
}
