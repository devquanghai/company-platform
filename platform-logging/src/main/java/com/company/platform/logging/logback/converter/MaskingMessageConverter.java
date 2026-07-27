package com.company.platform.logging.logback.converter;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

public final class MaskingMessageConverter extends ClassicConverter {
    @Override public String convert(ILoggingEvent event) {
        return event == null ? "" : BootstrapLogSanitizer.sanitize(
            event.getFormattedMessage());
    }
}
