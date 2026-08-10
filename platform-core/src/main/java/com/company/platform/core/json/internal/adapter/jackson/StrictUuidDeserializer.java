package com.company.platform.core.json.internal.adapter.jackson;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.deser.std.StdDeserializer;
import tools.jackson.databind.exc.InvalidFormatException;

import java.util.UUID;

public final class StrictUuidDeserializer
    extends StdDeserializer<UUID> {

    public StrictUuidDeserializer() {
        super(UUID.class);
    }

    @Override
    public UUID deserialize(
        JsonParser parser,
        DeserializationContext context
    ) {

        String value = parser.getValueAsString();

        try {
            return UUID.fromString(value);
        } catch (Exception exception) {
            throw InvalidFormatException.from(
                parser,
                "validation.uuid",
                value,
                UUID.class
            );
        }
    }
}
