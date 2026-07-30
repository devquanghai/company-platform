package com.company.platform.queue.serialization.registry;

import com.company.platform.queue.serialization.MessageSerializationFormat;
import com.company.platform.queue.serialization.MessageSerializer;

public interface MessageSerializerRegistry {
    MessageSerializer require(MessageSerializationFormat format);
}
