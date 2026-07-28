package com.company.platform.core.config.jackson.deprecated;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.deser.std.StdDeserializer;
import tools.jackson.databind.exc.InvalidFormatException;

public final class StrictIntegerDeserializer
    extends StdDeserializer<Integer> {

    public StrictIntegerDeserializer() {
        super(Integer.class);
    }

    @Override
    public Integer deserialize(
        JsonParser parser,
        DeserializationContext context
    ) {

        if (!parser.hasToken(JsonToken.VALUE_NUMBER_INT)) {

            throw InvalidFormatException.from(
                parser,
                "validation.integer",
                parser.getText(),
                Integer.class
            );
        }

        return parser.getIntValue();
    }
}
