package com.company.platform.logging.api.logger;

import com.company.platform.logging.domain.model.LogCategory;
import com.company.platform.logging.domain.model.LogSeverity;

import java.util.Map;

/**
 * Internal compatibility contract used by the annotation aspect.
 *
 * <p>Application code should use Lombok {@code @Slf4j} and the SLF4J 2 fluent
 * API. This contract is not a replacement logging facade; its implementation
 * delegates to SLF4J and only applies the platform masking and audit policy.</p>
 */
public interface PlatformLogger {
    void trace(String eventName, String message, Map<String, ?> fields);
    void debug(String eventName, String message, Map<String, ?> fields);
    void info(String eventName, String message, Map<String, ?> fields);
    void warn(String eventName, String message, Map<String, ?> fields);
    void error(String eventName, String message, Map<String, ?> fields, Throwable throwable);

    default void log(
        LogSeverity severity, LogCategory category, String eventName,
        String message, Map<String, ?> fields, Throwable throwable
    ) {
        switch (severity) {
            case TRACE -> trace(eventName, message, fields);
            case DEBUG -> debug(eventName, message, fields);
            case INFO -> info(eventName, message, fields);
            case WARN -> warn(eventName, message, fields);
            case ERROR -> error(eventName, message, fields, throwable);
        }
    }
}
