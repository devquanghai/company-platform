package com.company.platform.core.config.jackson.deprecated;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.deser.std.StdDeserializer;
import tools.jackson.databind.exc.InvalidFormatException;

import java.math.BigDecimal;

public final class StrictBigDecimalDeserializer
    extends StdDeserializer<BigDecimal> {

    public StrictBigDecimalDeserializer() {
        super(BigDecimal.class);
    }

    @Override
    public BigDecimal deserialize(
        JsonParser parser,
        DeserializationContext context
    ) {

        JsonToken token = parser.currentToken();

        if (token != JsonToken.VALUE_NUMBER_INT
            && token != JsonToken.VALUE_NUMBER_FLOAT) {

            throw InvalidFormatException.from(
                parser,
                "validation.decimal",
                parser.getText(),
                BigDecimal.class
            );
        }

        return parser.getDecimalValue();
    }
}
