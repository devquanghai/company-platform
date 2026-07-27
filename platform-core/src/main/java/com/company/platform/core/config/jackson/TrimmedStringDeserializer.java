package com.company.platform.core.config.jackson;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.deser.std.StdDeserializer;
import tools.jackson.databind.exc.MismatchedInputException;

public final class TrimmedStringDeserializer extends StdDeserializer<String> {

    public TrimmedStringDeserializer() {
        super(String.class);
    }

    @Override
    public String deserialize(JsonParser parser, DeserializationContext context) throws JacksonException {
        if (!parser.hasToken(JsonToken.VALUE_STRING)) {
            throw MismatchedInputException.from(parser, String.class, "Expected a JSON string");
        }
        return parser.getString().trim();
    }
}
