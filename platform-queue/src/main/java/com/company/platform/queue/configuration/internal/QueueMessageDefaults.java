package com.company.platform.queue.configuration.internal;

import com.company.platform.queue.autoconfigure.properties.MessageProperties;
import com.company.platform.queue.autoconfigure.properties.QueueDefaultsProperties;
import com.company.platform.queue.envelope.validation.MessageLimits;

public final class QueueMessageDefaults {
    public static final String CONTENT_TYPE = "application/json";

    private QueueMessageDefaults() {
    }

    public static MessageLimits limits(MessageProperties properties) {
        return limits(properties, null);
    }

    public static MessageLimits limits(
        MessageProperties properties, QueueDefaultsProperties legacy
    ) {
        MessageLimits defaults = MessageLimits.DEFAULT;
        long payloadBytes = properties.getMaxPayloadSize().toBytes();
        long envelopeBytes = properties.getMaxEnvelopeSize().toBytes();
        if (payloadBytes > Integer.MAX_VALUE || envelopeBytes > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("message sizes exceed supported range");
        }
        return new MessageLimits(
            legacy == null || legacy.getMaxHeaders() == null
                ? defaults.maxHeaders() : legacy.getMaxHeaders(),
            legacy == null || legacy.getMaxHeaderBytes() == null
                ? defaults.maxHeaderBytes() : legacy.getMaxHeaderBytes(),
            legacy == null || legacy.getMaxTotalHeaderBytes() == null
                ? defaults.maxTotalHeaderBytes() : legacy.getMaxTotalHeaderBytes(),
            (int) payloadBytes, (int) envelopeBytes);
    }
}
