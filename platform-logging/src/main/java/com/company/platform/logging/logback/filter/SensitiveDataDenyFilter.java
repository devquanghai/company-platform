package com.company.platform.logging.logback.filter;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;
import com.company.platform.logging.logback.converter.BootstrapLogSanitizer;
import org.slf4j.event.KeyValuePair;

import java.util.List;
import java.util.Map;

public final class SensitiveDataDenyFilter extends Filter<ILoggingEvent> {
    @Override
    public FilterReply decide(ILoggingEvent event) {
        if (event == null) {
            return FilterReply.NEUTRAL;
        }
        if (changed(event.getFormattedMessage())) {
            return FilterReply.DENY;
        }
        for (Map.Entry<String, String> entry : event.getMDCPropertyMap().entrySet()) {
            if (!BootstrapLogSanitizer.sanitize(entry.getKey(), entry.getValue())
                .equals(nullSafe(entry.getValue()))) {
                return FilterReply.DENY;
            }
        }
        List<KeyValuePair> pairs = event.getKeyValuePairs();
        if (pairs != null) {
            for (KeyValuePair pair : pairs) {
                if (!isScalar(pair.value)
                    || !BootstrapLogSanitizer.sanitize(pair.key, pair.value)
                        .equals(nullSafe(pair.value))) {
                    return FilterReply.DENY;
                }
            }
        }
        return unsafe(event.getThrowableProxy(), 0)
            ? FilterReply.DENY : FilterReply.NEUTRAL;
    }

    private static boolean unsafe(IThrowableProxy throwable, int depth) {
        if (throwable == null || depth >= 8) {
            return false;
        }
        if (changed(throwable.getMessage())
            || unsafe(throwable.getCause(), depth + 1)) {
            return true;
        }
        IThrowableProxy[] suppressed = throwable.getSuppressed();
        if (suppressed != null) {
            for (IThrowableProxy value : suppressed) {
                if (unsafe(value, depth + 1)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean changed(String value) {
        return !BootstrapLogSanitizer.sanitize(value).equals(nullSafe(value));
    }

    private static boolean isScalar(Object value) {
        return value == null || value instanceof CharSequence
            || value instanceof Number || value instanceof Boolean
            || value instanceof Enum<?>;
    }

    private static String nullSafe(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
