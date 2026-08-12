package com.company.platform.queue.configuration.internal;

import com.company.platform.queue.autoconfigure.properties.MessageProperties;
import com.company.platform.queue.envelope.validation.MessageLimits;

public final class QueueMessageDefaults {
    public static final String CONTENT_TYPE = "application/json";

    private QueueMessageDefaults() {
    }

    public static MessageLimits limits(MessageProperties properties) {
        long payloadBytes = properties.getMaxPayloadSize().toBytes();
        long envelopeBytes = properties.getMaxEnvelopeSize().toBytes();
        if (payloadBytes > Integer.MAX_VALUE || envelopeBytes > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("message sizes exceed supported range");
        }
        return new MessageLimits(
            properties.getMaxHeaders(),
            properties.getMaxHeaderBytes(),
            properties.getMaxTotalHeaderBytes(),
            (int) payloadBytes, (int) envelopeBytes);
    }
}
