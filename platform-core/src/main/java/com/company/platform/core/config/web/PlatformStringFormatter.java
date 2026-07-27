package com.company.platform.core.config.web;

import org.springframework.format.Formatter;

import java.text.ParseException;
import java.util.Locale;

public final class PlatformStringFormatter implements Formatter<String> {

    @Override
    public String parse(String text, Locale locale) throws ParseException {
        return text == null ? null : text.trim();
    }

    @Override
    public String print(String object, Locale locale) {
        return object;
    }
}
