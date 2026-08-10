package com.company.platform.core.json.internal.adapter.jackson;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.deser.std.StdDeserializer;
import tools.jackson.databind.exc.InvalidFormatException;

import java.time.Instant;
import java.time.format.DateTimeParseException;

public final class StrictInstantDeserializer
    extends StdDeserializer<Instant> {

    public StrictInstantDeserializer() {
        super(Instant.class);
    }

    @Override
    public Instant deserialize(
        JsonParser parser,
        DeserializationContext context
    ) {

        String value = parser.getValueAsString();

        try {
            return Instant.parse(value);
        } catch (DateTimeParseException exception) {
            throw InvalidFormatException.from(
                parser,
                "validation.instant.format",
                value,
                Instant.class
            );
        }
    }
}
