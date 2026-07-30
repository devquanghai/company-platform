package com.company.platform.queue.serialization.json;

import com.company.platform.core.json.JsonMapperHelper;
import com.company.platform.queue.api.model.MessageEnvelope;
import com.company.platform.queue.domain.exception.QueuePublishException;
import com.company.platform.queue.serialization.MessageSerializationContext;
import com.company.platform.queue.serialization.MessageSerializationFormat;
import com.company.platform.queue.serialization.MessageSerializer;

import java.util.Objects;

public final class JsonMessageSerializer implements MessageSerializer {
    private final JsonMapperHelper json;

    public JsonMessageSerializer(JsonMapperHelper json) {
        this.json = Objects.requireNonNull(json, "json");
    }

    @Override
    public MessageSerializationFormat format() {
        return MessageSerializationFormat.JSON;
    }

    @Override
    public byte[] serialize(Object payload, MessageSerializationContext context) {
        try {
            byte[] bytes = json.toBytes(payload);
            validateSize(bytes, context);
            return bytes;
        } catch (RuntimeException exception) {
            throw new QueuePublishException(
                "QUEUE.SERIALIZATION_FAILED", "Message serialization failed", exception);
        }
    }

    @Override
    public <T> T deserialize(
        byte[] payload, Class<T> targetType, MessageSerializationContext context
    ) {
        try {
            validateSize(payload, context);
            return json.fromBytes(payload, targetType);
        } catch (RuntimeException exception) {
            throw new QueuePublishException(
                "QUEUE.DESERIALIZATION_FAILED", "Message deserialization failed", exception);
        }
    }

    @Override
    public <T> MessageEnvelope<T> deserializeEnvelope(
        byte[] payload,
        Class<T> payloadType,
        MessageSerializationContext context
    ) {
        try {
            validateSize(payload, context);
            var type = json.getJsonMapper().getTypeFactory()
                .constructParametricType(MessageEnvelope.class, payloadType);
            return json.getJsonMapper().readValue(payload, type);
        } catch (RuntimeException exception) {
            throw new QueuePublishException(
                "QUEUE.DESERIALIZATION_FAILED",
                "Message deserialization failed", exception);
        }
    }

    private void validateSize(byte[] bytes, MessageSerializationContext context) {
        Objects.requireNonNull(bytes, "bytes");
        Objects.requireNonNull(context, "context");
        if (bytes.length > context.maxPayloadBytes()) {
            throw new IllegalArgumentException("payload exceeds configured limit");
        }
    }
}
