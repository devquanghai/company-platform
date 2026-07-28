package com.company.platform.core.config.jackson.deprecated;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.deser.std.StdDeserializer;
import tools.jackson.databind.exc.InvalidFormatException;

public final class StrictDoubleDeserializer
    extends StdDeserializer<Double> {

    public StrictDoubleDeserializer() {
        super(Double.class);
    }

    @Override
    public Double deserialize(
        JsonParser parser,
        DeserializationContext context
    ) {

        JsonToken token = parser.currentToken();

        if (token != JsonToken.VALUE_NUMBER_INT
            && token != JsonToken.VALUE_NUMBER_FLOAT) {

            throw InvalidFormatException.from(
                parser,
                "validation.double",
                parser.getText(),
                Double.class
            );
        }

        return parser.getDoubleValue();
    }
}
