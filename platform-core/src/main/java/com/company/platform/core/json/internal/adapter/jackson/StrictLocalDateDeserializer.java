package com.company.platform.core.json.internal.adapter.jackson;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.deser.std.StdDeserializer;
import tools.jackson.databind.exc.InvalidFormatException;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;

public final class StrictLocalDateDeserializer
    extends StdDeserializer<LocalDate> {

    private static final DateTimeFormatter FORMATTER =
        DateTimeFormatter.ofPattern("uuuu-MM-dd")
            .withResolverStyle(ResolverStyle.STRICT);

    public StrictLocalDateDeserializer() {
        super(LocalDate.class);
    }

    @Override
    public LocalDate deserialize(
        JsonParser parser,
        DeserializationContext context
    ) {

        String value = parser.getValueAsString();

        if (value.isBlank()) {
            return null;
        }

        try {

            return LocalDate.parse(
                value.trim(),
                FORMATTER
            );

        } catch (DateTimeParseException exception) {

            throw InvalidFormatException.from(
                parser,
                "validation.date.format",
                value,
                LocalDate.class
            );
        }
    }
}
