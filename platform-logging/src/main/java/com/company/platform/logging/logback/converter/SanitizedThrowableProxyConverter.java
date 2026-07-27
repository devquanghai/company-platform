package com.company.platform.logging.logback.converter;

import ch.qos.logback.classic.pattern.ThrowableProxyConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

public final class SanitizedThrowableProxyConverter extends ThrowableProxyConverter {
    private static final ThreadLocal<Boolean> ACTIVE =
        ThreadLocal.withInitial(() -> Boolean.FALSE);

    @Override
    public String convert(ILoggingEvent event) {
        if (Boolean.TRUE.equals(ACTIVE.get())) {
            return BootstrapLogSanitizer.SAFE_FAILURE;
        }
        ACTIVE.set(Boolean.TRUE);
        try {
            return BootstrapLogSanitizer.sanitize(super.convert(event));
        } catch (RuntimeException exception) {
            return BootstrapLogSanitizer.SAFE_FAILURE;
        } finally {
            ACTIVE.remove();
        }
    }
}
