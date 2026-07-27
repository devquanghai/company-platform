package com.company.platform.logging.application;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.company.platform.core.context.CurrentUser;
import com.company.platform.core.context.CurrentUserProvider;
import com.company.platform.core.context.RequestContextProvider;
import com.company.platform.core.time.TimeProvider;
import com.company.platform.core.trace.CurrentTraceContext;
import com.company.platform.core.trace.TraceContextProvider;
import com.company.platform.logging.api.masking.DataMaskingService;
import com.company.platform.logging.application.service.DefaultPlatformLogger;
import com.company.platform.logging.audit.event.LoggingAuditEvent;
import com.company.platform.logging.audit.publisher.LoggingAuditEventPublisher;
import com.company.platform.logging.autoconfigure.properties.PlatformLoggingProperties;
import com.company.platform.logging.domain.model.LogCategory;
import com.company.platform.logging.domain.model.LogSeverity;
import com.company.platform.logging.domain.model.SanitizedThrowable;
import com.company.platform.logging.observability.metrics.LoggingMetrics;
import com.company.platform.logging.structured.customizer.PlatformLogEventCustomizer;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Scope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.event.KeyValuePair;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultPlatformLoggerTest {
    private static final OffsetDateTime NOW =
        OffsetDateTime.of(2026, 7, 24, 10, 15, 30, 0, ZoneOffset.UTC);

    private Logger backend;
    private ListAppender<ILoggingEvent> appender;
    private Level originalLevel;

    @BeforeEach
    void attachAppender() {
        backend = (Logger) LoggerFactory.getLogger("PLATFORM");
        originalLevel = backend.getLevel();
        backend.setLevel(Level.TRACE);
        appender = new ListAppender<>();
        appender.start();
        backend.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        backend.detachAppender(appender);
        backend.setLevel(originalLevel);
        appender.stop();
    }

    @Test
    void writesAllSeveritiesWithSanitizedEnrichedAndCustomizedFields() {
        RecordingMetrics metrics = new RecordingMetrics();
        PlatformLogEventCustomizer successful = event -> {
            event.put("custom", "added");
            event.put("event.name", "cannot-overwrite");
            event.put(null, "ignored");
            event.put("null-value", null);
        };
        PlatformLogEventCustomizer failing = event -> {
            throw new IllegalStateException("customizer failed");
        };
        DefaultPlatformLogger logger = logger(
            new SafeMasking(), null, metrics,
            PlatformLoggingProperties.AuditFailureMode.FAIL_OPEN,
            PlatformLoggingProperties.UserIdMode.HASH,
            () -> Optional.of(user("operator-1")), List.of(successful, failing),
            new CurrentTraceContext("trace-1", "span-1"));

        logger.trace("user login!", "password=raw\nnext", Map.of("password", "raw"));
        logger.debug("debug", "message", Map.of());
        logger.info("info", "message", Map.of());
        logger.warn("warn", "message", Map.of());
        logger.error("error", "message", Map.of(), new IllegalArgumentException("raw"));

        assertThat(appender.list).hasSize(5);
        ILoggingEvent first = appender.list.getFirst();
        assertThat(first.getFormattedMessage()).isEqualTo("password=*** next");
        assertThat(keyValues(first))
            .containsEntry("event.name", "USER_LOGIN_")
            .containsEntry("event.category", "APPLICATION")
            .containsEntry("event.severity", "TRACE")
            .containsEntry("password", "***")
            .containsEntry("custom", "added")
            .containsEntry("request.id", "request-1")
            .containsEntry("correlation.id", "correlation-1")
            .containsEntry("trace.id", "trace-1")
            .containsEntry("span.id", "span-1")
            .containsEntry("event.timestamp", NOW.toString());
        assertThat(keyValues(first).get("user.id").toString())
            .startsWith("hmac-sha256:")
            .doesNotContain("operator-1");
        assertThat(keyValues(appender.list.getLast()))
            .containsKey("error");
        assertThat(metrics.logCalls).hasSize(5)
            .allSatisfy(call -> assertThat(call).endsWith(":true"));
    }

    @Test
    void normalizesMissingAndLongEventNamesAndHonorsDisabledBackend() {
        DefaultPlatformLogger logger = logger(
            new SafeMasking(), null, null,
            PlatformLoggingProperties.AuditFailureMode.FAIL_OPEN,
            PlatformLoggingProperties.UserIdMode.OMIT,
            Optional::empty, List.of(), null);

        logger.info(null, "one", null);
        logger.info(" ".repeat(4), "two", Map.of());
        logger.info("x".repeat(200), "three", Map.of());

        assertThat(keyValues(appender.list.get(0))).containsEntry(
            "event.name", "APPLICATION_EVENT");
        assertThat(keyValues(appender.list.get(1))).containsEntry(
            "event.name", "APPLICATION_EVENT");
        assertThat(keyValues(appender.list.get(2)).get("event.name").toString())
            .hasSize(128);

        backend.setLevel(Level.OFF);
        logger.error("disabled", "not-written", Map.of(), null);
        assertThat(appender.list).hasSize(3);
    }

    @Test
    void supportsMaskedAndOmittedUsersAndSkipsBlankContextValues() {
        CurrentUserProvider user = () -> Optional.of(user("operator-1"));
        DefaultPlatformLogger masked = logger(
            new SafeMasking(), null, null,
            PlatformLoggingProperties.AuditFailureMode.FAIL_OPEN,
            PlatformLoggingProperties.UserIdMode.MASK, user, List.of(),
            CurrentTraceContext.empty());
        masked.info("masked", "ok", Map.of());
        assertThat(keyValues(appender.list.getLast()))
            .containsEntry("user.id", "masked:operator-1")
            .doesNotContainKeys("trace.id", "span.id");

        DefaultPlatformLogger omitted = new DefaultPlatformLogger(
            new SafeMasking(), new EmptyRequestContext(), () -> null,
            new FixedTime(), user, List.of(), null, null,
            PlatformLoggingProperties.AuditFailureMode.FAIL_OPEN,
            PlatformLoggingProperties.UserIdMode.OMIT);
        omitted.info("omitted", "ok", Map.of());
        assertThat(keyValues(appender.list.getLast()))
            .doesNotContainKeys("user.id", "request.id", "correlation.id",
                "trace.id", "span.id");

        CurrentUser blank = user(" ");
        DefaultPlatformLogger blankUser = logger(
            new SafeMasking(), null, null,
            PlatformLoggingProperties.AuditFailureMode.FAIL_OPEN,
            PlatformLoggingProperties.UserIdMode.HASH,
            () -> Optional.of(blank), List.of(), null);
        blankUser.info("blank", "ok", Map.of());
        assertThat(keyValues(appender.list.getLast())).doesNotContainKey("user.id");
    }

    @Test
    void prefersCurrentOpenTelemetrySpanOverLegacyTraceProvider() {
        SpanContext spanContext = SpanContext.create(
            "0123456789abcdef0123456789abcdef",
            "0123456789abcdef",
            TraceFlags.getSampled(),
            TraceState.getDefault());
        DefaultPlatformLogger logger = logger(
            new SafeMasking(), null, null,
            PlatformLoggingProperties.AuditFailureMode.FAIL_OPEN,
            PlatformLoggingProperties.UserIdMode.OMIT,
            Optional::empty, List.of(),
            new CurrentTraceContext("fallback-trace", "fallback-span"));

        try (Scope ignored = Span.wrap(spanContext).makeCurrent()) {
            logger.info("otel", "ok", Map.of());
        }

        assertThat(keyValues(appender.list.getLast()))
            .containsEntry("trace.id", spanContext.getTraceId())
            .containsEntry("span.id", spanContext.getSpanId())
            .doesNotContainValue("fallback-trace")
            .doesNotContainValue("fallback-span");
    }

    @Test
    void publishesAuditAndSecurityEventsWithSuccessAndFailureDetails() {
        List<LoggingAuditEvent> events = new ArrayList<>();
        DefaultPlatformLogger logger = logger(
            new SafeMasking(), events::add, null,
            PlatformLoggingProperties.AuditFailureMode.FAIL_OPEN,
            PlatformLoggingProperties.UserIdMode.OMIT,
            Optional::empty, List.of(), new CurrentTraceContext("trace-1", "span-1"));

        logger.log(LogSeverity.INFO, LogCategory.AUDIT, "create", "ok", Map.of(), null);
        logger.log(LogSeverity.ERROR, LogCategory.AUDIT, "update", "failed",
            Map.of(), new IllegalStateException("raw"));
        logger.log(LogSeverity.ERROR, LogCategory.SECURITY, "denied", "failed",
            Map.of(), new SecurityException("raw"));
        logger.log(LogSeverity.INFO, LogCategory.APPLICATION, "plain", "ok",
            Map.of(), null);

        assertThat(events).hasSize(3);
        assertThat(events.get(0).getEventType()).isEqualTo("PLATFORM_AUDIT_LOG");
        assertThat(events.get(0).getOutcome()).isEqualTo("SUCCESS");
        assertThat(events.get(0).getTraceId()).isEqualTo("trace-1");
        assertThat(events.get(0).getRequestId()).isEqualTo("request-1");
        assertThat(events.get(1).getEventType()).isEqualTo("PLATFORM_AUDIT_LOG");
        assertThat(events.get(1).getOutcome()).isEqualTo("FAILED");
        assertThat(events.get(2).getEventType()).isEqualTo("PLATFORM_SECURITY_LOG");
        assertThat(events.get(2).getOutcome()).isEqualTo("FAILED");
        assertThat(events.get(2).getErrorType()).isEqualTo(SecurityException.class.getName());
    }

    @Test
    void auditFailurePolicyIsFailClosedOnlyForAuditCategory() {
        LoggingAuditEventPublisher failingAudit = event -> {
            throw new IllegalStateException("publisher unavailable");
        };
        RecordingMetrics metrics = new RecordingMetrics();
        DefaultPlatformLogger failClosed = logger(
            new SafeMasking(), failingAudit, metrics,
            PlatformLoggingProperties.AuditFailureMode.FAIL_CLOSED,
            PlatformLoggingProperties.UserIdMode.OMIT,
            Optional::empty, List.of(), null);

        assertThatThrownBy(() -> failClosed.log(
            LogSeverity.INFO, LogCategory.AUDIT, "audit", "message", Map.of(), null))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("publisher unavailable");
        assertThat(metrics.logCalls).contains("INFO:AUDIT:false");

        failClosed.log(LogSeverity.INFO, LogCategory.SECURITY,
            "security", "message", Map.of(), null);
        assertThat(metrics.logCalls).contains("INFO:SECURITY:false");

        DefaultPlatformLogger failOpen = logger(
            new ThrowingMasking(), null, metrics,
            PlatformLoggingProperties.AuditFailureMode.FAIL_OPEN,
            PlatformLoggingProperties.UserIdMode.OMIT,
            Optional::empty, List.of(), null);
        failOpen.info("application", "message", Map.of());
        assertThat(metrics.logCalls).contains("INFO:APPLICATION:false");
    }

    @Test
    void preventsRecursiveAuditPublication() {
        java.util.concurrent.atomic.AtomicInteger publications =
            new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.atomic.AtomicReference<DefaultPlatformLogger> reference =
            new java.util.concurrent.atomic.AtomicReference<>();
        LoggingAuditEventPublisher publisher = event -> {
            publications.incrementAndGet();
            reference.get().log(LogSeverity.INFO, LogCategory.AUDIT,
                "nested", "nested", Map.of(), null);
        };
        DefaultPlatformLogger logger = logger(
            new SafeMasking(), publisher, null,
            PlatformLoggingProperties.AuditFailureMode.FAIL_OPEN,
            PlatformLoggingProperties.UserIdMode.OMIT,
            Optional::empty, List.of(), null);
        reference.set(logger);

        logger.log(LogSeverity.INFO, LogCategory.AUDIT,
            "outer", "outer", Map.of(), null);

        assertThat(publications).hasValue(1);
        assertThat(appender.list).hasSize(2);
    }

    @Test
    void metricsFailuresNeverChangeLoggingBehaviorAndConvenienceConstructorWorks() {
        LoggingMetrics failingMetrics = new RecordingMetrics() {
            @Override
            public void recordLog(LogSeverity level, LogCategory category, boolean success) {
                throw new IllegalStateException("metrics unavailable");
            }
        };
        DefaultPlatformLogger logger = logger(
            new SafeMasking(), null, failingMetrics,
            PlatformLoggingProperties.AuditFailureMode.FAIL_OPEN,
            PlatformLoggingProperties.UserIdMode.OMIT,
            Optional::empty, List.of(), null);
        logger.info("still-logged", "message", Map.of());
        assertThat(appender.list.getLast().getFormattedMessage()).isEqualTo("message");

        DefaultPlatformLogger simple = new DefaultPlatformLogger(
            new SafeMasking(), new FixedRequestContext(), () -> null,
            new FixedTime(), null, List.of());
        simple.info("simple", "message", Map.of());
        assertThat(appender.list).hasSize(2);

        DefaultPlatformLogger withAudit = new DefaultPlatformLogger(
            new SafeMasking(), new FixedRequestContext(), () -> null,
            new FixedTime(), null, List.of(), event -> { }, null,
            PlatformLoggingProperties.AuditFailureMode.FAIL_OPEN);
        withAudit.info("with-audit", "message", Map.of());
        assertThat(appender.list).hasSize(3);
    }

    private static DefaultPlatformLogger logger(
        DataMaskingService masking, LoggingAuditEventPublisher audit,
        LoggingMetrics metrics, PlatformLoggingProperties.AuditFailureMode mode,
        PlatformLoggingProperties.UserIdMode userIdMode, CurrentUserProvider users,
        List<PlatformLogEventCustomizer> customizers, CurrentTraceContext trace
    ) {
        return new DefaultPlatformLogger(
            masking, new FixedRequestContext(), () -> trace, new FixedTime(),
            users, customizers, audit, metrics, mode, userIdMode,
            alias -> "01234567890123456789012345678901".getBytes(), "user-hash");
    }

    private static Map<String, Object> keyValues(ILoggingEvent event) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        List<KeyValuePair> pairs = event.getKeyValuePairs();
        if (pairs != null) {
            pairs.forEach(pair -> result.put(pair.key, pair.value));
        }
        return result;
    }

    private static CurrentUser user(String id) {
        CurrentUser user = new CurrentUser();
        user.setUserId(id);
        return user;
    }

    private static final class FixedRequestContext implements RequestContextProvider {
        @Override public String getRequestId() { return "request-1"; }
        @Override public String getCorrelationId() { return "correlation-1"; }
    }

    private static final class EmptyRequestContext implements RequestContextProvider {
        @Override public String getRequestId() { return " "; }
        @Override public String getCorrelationId() { return null; }
    }

    private static final class FixedTime implements TimeProvider {
        @Override public Instant nowInstant() { return NOW.toInstant(); }
        @Override public OffsetDateTime now() { return NOW; }
        @Override public OffsetDateTime now(ZoneId zoneId) {
            return NOW.atZoneSameInstant(zoneId).toOffsetDateTime();
        }
        @Override public ZoneId getDefaultZone() { return ZoneOffset.UTC; }
    }

    private static class RecordingMetrics implements LoggingMetrics {
        private final List<String> logCalls = new ArrayList<>();
        @Override public void recordLog(
            LogSeverity level, LogCategory category, boolean success
        ) {
            logCalls.add(level + ":" + category + ":" + success);
        }
        @Override public void recordMasking(
            com.company.platform.logging.domain.model.MaskingType type,
            com.company.platform.logging.domain.model.PiiType piiType,
            boolean removed
        ) { }
        @Override public void recordCrypto(
            com.company.platform.logging.domain.model.CryptoProviderType provider,
            com.company.platform.logging.domain.model.CryptoAlgorithm algorithm,
            com.company.platform.logging.domain.model.CryptoOperation operation,
            boolean success, java.time.Duration duration
        ) { }
    }

    private static class SafeMasking implements DataMaskingService {
        @Override
        public String maskValue(String fieldName, String value) {
            return "userId".equals(fieldName) ? "masked:" + value : "***";
        }
        @Override public Object sanitize(Object source) { return source; }
        @Override public String sanitizeJson(String json) { return json; }
        @Override
        public String sanitizeMessage(String message) {
            return message == null ? null
                : message.replace("raw", "***").replaceAll("[\\r\\n\\t]+", " ");
        }
        @Override
        public Map<String, Object> sanitizeFields(Map<String, ?> fields) {
            LinkedHashMap<String, Object> result = new LinkedHashMap<>();
            if (fields != null) {
                fields.forEach((key, value) ->
                    result.put(key, "password".equals(key) ? "***" : value));
            }
            return result;
        }
        @Override
        public SanitizedThrowable sanitizeThrowable(Throwable throwable) {
            return SanitizedThrowable.builder()
                .type(throwable.getClass().getName())
                .message("***")
                .stackTrace(List.of("safe-stack"))
                .build();
        }
    }

    private static final class ThrowingMasking extends SafeMasking {
        @Override public String sanitizeMessage(String message) {
            throw new IllegalStateException("masking unavailable");
        }
    }
}
