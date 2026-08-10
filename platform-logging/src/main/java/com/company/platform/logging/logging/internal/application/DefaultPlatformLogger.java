package com.company.platform.logging.logging.internal.application;

import com.company.platform.core.context.CurrentUser;
import com.company.platform.core.context.CurrentUserProvider;
import com.company.platform.core.context.RequestContextProvider;
import com.company.platform.core.time.TimeProvider;
import com.company.platform.core.trace.CurrentTraceContext;
import com.company.platform.core.trace.TraceContextProvider;
import com.company.platform.logging.api.logger.PlatformLogger;
import com.company.platform.logging.api.masking.DataMaskingService;
import com.company.platform.logging.api.masking.MaskingHashKeyProvider;
import com.company.platform.logging.audit.event.PlatformAuditLogEvent;
import com.company.platform.logging.audit.event.PlatformSecurityLogEvent;
import com.company.platform.logging.audit.publisher.LoggingAuditEventPublisher;
import com.company.platform.logging.autoconfigure.properties.PlatformLoggingProperties;
import com.company.platform.logging.domain.model.LogCategory;
import com.company.platform.logging.domain.model.LogSeverity;
import com.company.platform.logging.domain.model.SanitizedThrowable;
import com.company.platform.logging.domain.model.MaskingContext;
import com.company.platform.logging.masking.strategy.HashMaskingStrategy;
import com.company.platform.logging.structured.customizer.MutablePlatformLogEvent;
import com.company.platform.logging.structured.customizer.PlatformLogEventCustomizer;
import com.company.platform.logging.observability.metrics.LoggingMetrics;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.spi.LoggingEventBuilder;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j(topic = "PLATFORM")
public final class DefaultPlatformLogger implements PlatformLogger {
    private final DataMaskingService masking;
    private final RequestContextProvider requests;
    private final TraceContextProvider traces;
    private final TimeProvider time;
    private final CurrentUserProvider users;
    private final List<PlatformLogEventCustomizer> customizers;
    private final LoggingAuditEventPublisher audit;
    private final LoggingMetrics metrics;
    private final PlatformLoggingProperties.AuditFailureMode auditFailureMode;
    private final PlatformLoggingProperties.UserIdMode userIdMode;
    private final MaskingHashKeyProvider userHashKeys;
    private final String userHashKeyAlias;
    private final ThreadLocal<Boolean> publishingAudit =
        ThreadLocal.withInitial(() -> Boolean.FALSE);

    public DefaultPlatformLogger(
        DataMaskingService masking, RequestContextProvider requests,
        TraceContextProvider traces, TimeProvider time,
        CurrentUserProvider users, List<PlatformLogEventCustomizer> customizers
    ) {
        this(masking, requests, traces, time, users, customizers,
            null, null, PlatformLoggingProperties.AuditFailureMode.FAIL_OPEN,
            PlatformLoggingProperties.UserIdMode.OMIT, null, null);
    }

    public DefaultPlatformLogger(
        DataMaskingService masking, RequestContextProvider requests,
        TraceContextProvider traces, TimeProvider time,
        CurrentUserProvider users, List<PlatformLogEventCustomizer> customizers,
        LoggingAuditEventPublisher audit, LoggingMetrics metrics,
        PlatformLoggingProperties.AuditFailureMode auditFailureMode
    ) {
        this(masking, requests, traces, time, users, customizers, audit, metrics,
            auditFailureMode, PlatformLoggingProperties.UserIdMode.OMIT, null, null);
    }

    public DefaultPlatformLogger(
        DataMaskingService masking, RequestContextProvider requests,
        TraceContextProvider traces, TimeProvider time,
        CurrentUserProvider users, List<PlatformLogEventCustomizer> customizers,
        LoggingAuditEventPublisher audit, LoggingMetrics metrics,
        PlatformLoggingProperties.AuditFailureMode auditFailureMode,
        PlatformLoggingProperties.UserIdMode userIdMode
    ) {
        this(masking, requests, traces, time, users, customizers, audit, metrics,
            auditFailureMode, userIdMode, null, null);
    }

    public DefaultPlatformLogger(
        DataMaskingService masking, RequestContextProvider requests,
        TraceContextProvider traces, TimeProvider time,
        CurrentUserProvider users, List<PlatformLogEventCustomizer> customizers,
        LoggingAuditEventPublisher audit, LoggingMetrics metrics,
        PlatformLoggingProperties.AuditFailureMode auditFailureMode,
        PlatformLoggingProperties.UserIdMode userIdMode,
        MaskingHashKeyProvider userHashKeys, String userHashKeyAlias
    ) {
        this.masking = masking;
        this.requests = requests;
        this.traces = traces;
        this.time = time;
        this.users = users;
        this.customizers = List.copyOf(customizers);
        this.audit = audit;
        this.metrics = metrics;
        this.auditFailureMode = auditFailureMode;
        this.userIdMode = userIdMode;
        this.userHashKeys = userHashKeys;
        this.userHashKeyAlias = userHashKeyAlias;
    }

    @Override public void trace(String event, String message, Map<String, ?> fields) {
        write(LogSeverity.TRACE, event, message, fields, null);
    }
    @Override public void debug(String event, String message, Map<String, ?> fields) {
        write(LogSeverity.DEBUG, event, message, fields, null);
    }
    @Override public void info(String event, String message, Map<String, ?> fields) {
        write(LogSeverity.INFO, event, message, fields, null);
    }
    @Override public void warn(String event, String message, Map<String, ?> fields) {
        write(LogSeverity.WARN, event, message, fields, null);
    }
    @Override public void error(
        String event, String message, Map<String, ?> fields, Throwable throwable
    ) {
        write(LogSeverity.ERROR, event, message, fields, throwable);
    }

    public void write(
        LogSeverity severity, String eventName, String message,
        Map<String, ?> fields, Throwable throwable
    ) {
        log(severity, LogCategory.APPLICATION, eventName, message, fields, throwable);
    }

    @Override
    public void log(
        LogSeverity severity, LogCategory category, String eventName, String message,
        Map<String, ?> fields, Throwable throwable
    ) {
        if (!enabled(severity)) {
            return;
        }
        try {
            writeInternal(severity, category, eventName, message, fields, throwable);
            recordMetric(severity, category, true);
        } catch (RuntimeException exception) {
            recordMetric(severity, category, false);
            if (auditFailureMode == PlatformLoggingProperties.AuditFailureMode.FAIL_CLOSED
                && category == LogCategory.AUDIT) {
                throw exception;
            }
        }
    }

    private void writeInternal(
        LogSeverity severity, LogCategory category, String eventName, String message,
        Map<String, ?> fields, Throwable throwable
    ) {
        String event = safeEventName(eventName);
        String safeMessage = masking.sanitizeMessage(message);
        LinkedHashMap<String, Object> enriched =
            new LinkedHashMap<>(masking.sanitizeFields(fields));
        enrich(enriched);
        if (throwable != null) {
            SanitizedThrowable safe = masking.sanitizeThrowable(throwable);
            enriched.put("error", masking.sanitize(safe));
        }
        MutablePlatformLogEvent mutable = new MutablePlatformLogEvent(
            event, safeMessage, severity, category, enriched);
        customizers.forEach(customizer -> safelyCustomize(customizer, mutable));
        Map<String, Object> finalFields = masking.sanitizeFields(mutable.fieldsSnapshot());
        LoggingEventBuilder builder = builder(severity)
            .addKeyValue("event.name", event)
            .addKeyValue("event.category", category.name())
            .addKeyValue("event.severity", severity.name());
        finalFields.forEach(builder::addKeyValue);
        builder.log(safeMessage);
        publishAudit(category, event, throwable);
    }

    private void publishAudit(
        LogCategory category, String event, Throwable throwable
    ) {
        if (audit == null || Boolean.TRUE.equals(publishingAudit.get())
            || category != LogCategory.AUDIT && category != LogCategory.SECURITY) {
            return;
        }
        publishingAudit.set(Boolean.TRUE);
        try {
            CurrentTraceContext trace = currentTrace();
            String traceId = trace.getTraceId();
            if (category == LogCategory.AUDIT) {
                audit.publish(new PlatformAuditLogEvent(
                    time.now(), event, throwable == null ? "SUCCESS" : "FAILED",
                    traceId, requests.getRequestId()));
            } else {
                audit.publish(new PlatformSecurityLogEvent(
                    time.now(), event, throwable == null ? "SUCCESS" : "FAILED",
                    throwable == null ? null : throwable.getClass().getName(),
                    traceId, requests.getRequestId()));
            }
        } finally {
            publishingAudit.remove();
        }
    }

    private void enrich(Map<String, Object> fields) {
        put(fields, "request.id", requests.getRequestId());
        put(fields, "correlation.id", requests.getCorrelationId());
        CurrentTraceContext trace = currentTrace();
        if (trace.isAvailable()) {
            put(fields, "trace.id", trace.getTraceId());
            put(fields, "span.id", trace.getSpanId());
        }
        put(fields, "event.timestamp", time.now());
        Optional.ofNullable(users).flatMap(CurrentUserProvider::currentUser)
            .map(CurrentUser::getUserId).filter(value -> !value.isBlank())
            .ifPresent(value -> {
                if (userIdMode == PlatformLoggingProperties.UserIdMode.MASK) {
                    put(fields, "user.id", masking.maskValue("userId", value));
                } else if (userIdMode == PlatformLoggingProperties.UserIdMode.HASH) {
                    put(fields, "user.id", hashUserId(value));
                }
            });
    }

    private static void safelyCustomize(
        PlatformLogEventCustomizer customizer, MutablePlatformLogEvent event
    ) {
        try {
            customizer.customize(event);
        } catch (RuntimeException ignored) {
            // Logging customization is fail-open and raw input is never logged here.
        }
    }

    private boolean enabled(LogSeverity severity) {
        return switch (severity) {
            case TRACE -> log.isTraceEnabled();
            case DEBUG -> log.isDebugEnabled();
            case INFO -> log.isInfoEnabled();
            case WARN -> log.isWarnEnabled();
            case ERROR -> log.isErrorEnabled();
        };
    }

    private static LoggingEventBuilder builder(LogSeverity severity) {
        return switch (severity) {
            case TRACE -> log.atTrace();
            case DEBUG -> log.atDebug();
            case INFO -> log.atInfo();
            case WARN -> log.atWarn();
            case ERROR -> log.atError();
        };
    }

    private CurrentTraceContext currentTrace() {
        CurrentTraceContext current = traces == null ? null : traces.getCurrentContext();
        return current == null ? CurrentTraceContext.empty() : current;
    }

    private static String safeEventName(String value) {
        if (value == null || value.isBlank()) {
            return "APPLICATION_EVENT";
        }
        String safe = value.toUpperCase(java.util.Locale.ROOT)
            .replaceAll("[^A-Z0-9_.-]", "_");
        return safe.substring(0, Math.min(128, safe.length()));
    }

    private String hashUserId(String value) {
        if (userHashKeys == null || userHashKeyAlias == null
            || userHashKeyAlias.isBlank()) {
            return null;
        }
        return new HashMaskingStrategy(userHashKeys, userHashKeyAlias)
            .mask(value, MaskingContext.builder().build()).getValue();
    }

    private void recordMetric(
        LogSeverity severity, LogCategory category, boolean success
    ) {
        if (metrics == null) {
            return;
        }
        try {
            metrics.recordLog(severity, category, success);
        } catch (RuntimeException ignored) {
            // Metrics are fail-open and never alter application logging semantics.
        }
    }

    private static void put(Map<String, Object> fields, String key, Object value) {
        if (value != null && !(value instanceof String text && text.isBlank())) {
            fields.put(key, value instanceof OffsetDateTime ? value.toString() : value);
        }
    }
}
