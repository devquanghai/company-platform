package com.company.platform.queue.serialization;

public record MessageSerializationContext(
    String eventType,
    int schemaVersion,
    String contentType,
    int maxPayloadBytes
) {
}
