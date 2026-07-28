package com.company.platform.core.config.jackson.deprecated;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.deser.std.StdDeserializer;
import tools.jackson.databind.exc.InvalidFormatException;

public final class StrictLongDeserializer
    extends StdDeserializer<Long> {

    public StrictLongDeserializer() {
        super(Long.class);
    }

    @Override
    public Long deserialize(
        JsonParser parser,
        DeserializationContext context
    ) {

        if (!parser.hasToken(JsonToken.VALUE_NUMBER_INT)) {

            throw InvalidFormatException.from(
                parser,
                "validation.long",
                parser.getText(),
                Long.class
            );
        }

        return parser.getLongValue();
    }
}
