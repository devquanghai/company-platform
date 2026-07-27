package com.company.platform.logging.logback.converter;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

import java.util.TreeMap;
import java.util.stream.Collectors;

public final class MaskingMdcConverter extends ClassicConverter {
    @Override public String convert(ILoggingEvent event) {
        if (event == null || event.getMDCPropertyMap() == null) {
            return "";
        }
        return new TreeMap<>(event.getMDCPropertyMap()).entrySet().stream()
            .map(entry -> BootstrapLogSanitizer.sanitize(entry.getKey())
                + "=" + BootstrapLogSanitizer.sanitize(entry.getKey(), entry.getValue()))
            .collect(Collectors.joining(","));
    }
}
