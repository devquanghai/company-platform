package com.company.platform.queue.serialization.internal.adapter;

import com.company.platform.queue.serialization.MessageSerializationFormat;
import com.company.platform.queue.serialization.MessageSerializer;
import com.company.platform.queue.serialization.registry.MessageSerializerRegistry;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class DefaultMessageSerializerRegistry implements MessageSerializerRegistry {
    private final Map<MessageSerializationFormat, MessageSerializer> serializers;

    public DefaultMessageSerializerRegistry(List<MessageSerializer> serializers) {
        EnumMap<MessageSerializationFormat, MessageSerializer> values =
            new EnumMap<>(MessageSerializationFormat.class);
        for (MessageSerializer serializer : serializers) {
            if (values.put(serializer.format(), serializer) != null) {
                throw new IllegalArgumentException(
                    "duplicate serializer for " + serializer.format());
            }
        }
        this.serializers = Map.copyOf(values);
    }

    @Override
    public MessageSerializer require(MessageSerializationFormat format) {
        MessageSerializer serializer = serializers.get(format);
        if (serializer == null) {
            throw new IllegalArgumentException("serializer is not registered: " + format);
        }
        return serializer;
    }
}
