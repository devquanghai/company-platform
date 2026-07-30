package com.company.platform.queue.serialization;

import com.company.platform.queue.api.model.MessageEnvelope;

public interface MessageSerializer {
    MessageSerializationFormat format();
    byte[] serialize(Object payload, MessageSerializationContext context);
    <T> T deserialize(byte[] payload, Class<T> targetType, MessageSerializationContext context);

    @SuppressWarnings("unchecked")
    default <T> MessageEnvelope<T> deserializeEnvelope(
        byte[] payload,
        Class<T> payloadType,
        MessageSerializationContext context
    ) {
        return (MessageEnvelope<T>) deserialize(
            payload, MessageEnvelope.class, context);
    }
}
