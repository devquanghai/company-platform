package com.company.platform.queue.serialization.registry;

public interface MessageSchemaRegistry {
    MessageSchema resolve(String eventType, int version);
}
