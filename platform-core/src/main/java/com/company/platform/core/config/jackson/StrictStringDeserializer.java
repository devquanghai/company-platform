package com.company.platform.core.config.jackson;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.deser.std.StdDeserializer;
import tools.jackson.databind.exc.InvalidFormatException;
import tools.jackson.databind.exc.MismatchedInputException;

public final class StrictStringDeserializer
    extends StdDeserializer<String> {

    private final boolean allowUnicode;
    private final boolean allowSpecialCharacters;

    public StrictStringDeserializer(
        boolean allowUnicode,
        boolean allowSpecialCharacters
    ) {
        super(String.class);
        this.allowUnicode = allowUnicode;
        this.allowSpecialCharacters = allowSpecialCharacters;
    }

    @Override
    public String deserialize(
        JsonParser parser,
        DeserializationContext context
    ) throws JacksonException {

        if (!parser.hasToken(JsonToken.VALUE_STRING)) {

            throw MismatchedInputException.from(
                parser,
                String.class,
                "validation.string"
            );
        }

        String value = parser.getString();

        if (value == null) {
            return null;
        }

        value = value.trim();

        if (!allowUnicode
            && !value.matches("\\A\\p{ASCII}*\\z")) {

            throw InvalidFormatException.from(
                parser,
                "validation.string.unicode",
                value,
                String.class
            );
        }

        if (!allowSpecialCharacters
            && containsSpecialCharacter(value)) {

            throw InvalidFormatException.from(
                parser,
                "validation.string.special-character",
                value,
                String.class
            );
        }

        return value;
    }

    private static boolean containsSpecialCharacter(String value) {

        for (int i = 0; i < value.length(); i++) {

            char c = value.charAt(i);

            if (Character.isLetterOrDigit(c)
                || Character.isWhitespace(c)) {
                continue;
            }

            return true;
        }

        return false;
    }
}
