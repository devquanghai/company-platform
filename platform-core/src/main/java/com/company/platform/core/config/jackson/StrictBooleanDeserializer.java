package com.company.platform.core.config.jackson;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.deser.std.StdDeserializer;
import tools.jackson.databind.exc.InvalidFormatException;

public final class StrictBooleanDeserializer
    extends StdDeserializer<Boolean> {

    public StrictBooleanDeserializer() {
        super(Boolean.class);
    }

    @Override
    public Boolean deserialize(
        JsonParser parser,
        DeserializationContext context
    ) {

        if (!parser.hasToken(JsonToken.VALUE_TRUE)
            && !parser.hasToken(JsonToken.VALUE_FALSE)) {

            throw InvalidFormatException.from(
                parser,
                "validation.boolean",
                parser.currentToken().name(),
                Boolean.class
            );
        }

        return parser.getBooleanValue();
    }
}
