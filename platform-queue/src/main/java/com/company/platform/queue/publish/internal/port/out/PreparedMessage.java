package com.company.platform.queue.publish.internal.port.out;

import com.company.platform.queue.api.model.MessageEnvelope;

public record PreparedMessage(
    String broker,
    String destination,
    Object key,
    Integer partition,
    String routingKey,
    MessageEnvelope<?> envelope,
    byte[] body
) {
    public PreparedMessage {
        body = body.clone();
    }

    @Override
    public byte[] body() {
        return body.clone();
    }
}
