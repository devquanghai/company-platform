package com.company.platform.queue.serialization.registry;

public record MessageSchema(String eventType, int version, Class<?> javaType) {
}
