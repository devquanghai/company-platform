package com.company.platform.core.json.internal.adapter.jackson;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.deser.std.StdDeserializer;
import tools.jackson.databind.exc.InvalidFormatException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;

public final class StrictLocalDateTimeDeserializer
    extends StdDeserializer<LocalDateTime> {

    private static final DateTimeFormatter FORMATTER =
        DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss")
            .withResolverStyle(ResolverStyle.STRICT);

    public StrictLocalDateTimeDeserializer() {
        super(LocalDateTime.class);
    }

    @Override
    public LocalDateTime deserialize(
        JsonParser parser,
        DeserializationContext context
    ) {

        String value = parser.getValueAsString();

        try {
            return LocalDateTime.parse(value, FORMATTER);
        } catch (DateTimeParseException exception) {
            throw InvalidFormatException.from(
                parser,
                "validation.datetime.format",
                value,
                LocalDateTime.class
            );
        }
    }
}
