package com.company.platform.logging.logback.converter;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

import java.util.stream.Collectors;

public final class MaskingKeyValueConverter extends ClassicConverter {
    @Override public String convert(ILoggingEvent event) {
        if (event == null || event.getKeyValuePairs() == null) {
            return "";
        }
        return event.getKeyValuePairs().stream()
            .map(pair -> BootstrapLogSanitizer.sanitize(pair.key) + "="
                + BootstrapLogSanitizer.sanitize(pair.key, pair.value))
            .collect(Collectors.joining(","));
    }
}
