package com.company.platform.core.config.jackson;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.deser.std.StdDeserializer;
import tools.jackson.databind.exc.InvalidFormatException;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

public final class StrictOffsetDateTimeDeserializer
    extends StdDeserializer<OffsetDateTime> {

    public StrictOffsetDateTimeDeserializer() {
        super(OffsetDateTime.class);
    }

    @Override
    public OffsetDateTime deserialize(
        JsonParser parser,
        DeserializationContext context
    ) {

        String value = parser.getValueAsString();

        try {
            return OffsetDateTime.parse(value);
        } catch (DateTimeParseException exception) {
            throw InvalidFormatException.from(
                parser,
                "validation.offset-datetime.format",
                value,
                OffsetDateTime.class
            );
        }
    }
}
