package com.company.platform.queue.autoconfigure.properties;

import com.company.platform.queue.serialization.MessageSerializationFormat;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SerializationProperties {
    private MessageSerializationFormat format = MessageSerializationFormat.JSON;
    private String eventType;
    private int schemaVersion = 1;
}
