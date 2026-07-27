package com.company.platform.logging.structured;

import com.company.platform.logging.audit.event.CryptoOperationEvent;
import com.company.platform.logging.audit.event.LoggingAuditEvent;
import com.company.platform.logging.audit.event.MaskingAppliedEvent;
import com.company.platform.logging.audit.event.PlatformAuditLogEvent;
import com.company.platform.logging.audit.event.PlatformSecurityLogEvent;
import com.company.platform.logging.audit.publisher.SpringLoggingAuditEventPublisher;
import com.company.platform.logging.domain.model.LogCategory;
import com.company.platform.logging.domain.model.LogSeverity;
import com.company.platform.logging.structured.customizer.MutablePlatformLogEvent;
import com.company.platform.logging.structured.event.PlatformLogEvent;
import com.company.platform.logging.structured.event.PlatformLogField;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StructuredAndAuditModelTest {
    private static final OffsetDateTime NOW =
        OffsetDateTime.of(2026, 7, 24, 11, 0, 0, 0, ZoneOffset.UTC);

    @Test
    void mutableEventRejectsReservedNullFieldsAndReturnsImmutableSnapshot() {
        LinkedHashMap<String, Object> input = new LinkedHashMap<>();
        input.put("valid", 1);
        input.put("event.name", "override");
        input.put(null, "null-key");
        input.put("null-value", null);
        MutablePlatformLogEvent event = new MutablePlatformLogEvent(
            "ORDER_CREATED", "created", LogSeverity.INFO,
            LogCategory.BUSINESS, input);

        event.put("second", 2);
        event.put("event.message", "override");
        event.put(null, 3);
        event.put("ignored", null);
        Map<String, Object> snapshot = event.fieldsSnapshot();

        assertThat(event.getEventName()).isEqualTo("ORDER_CREATED");
        assertThat(event.getMessage()).isEqualTo("created");
        assertThat(event.getSeverity()).isEqualTo(LogSeverity.INFO);
        assertThat(event.getCategory()).isEqualTo(LogCategory.BUSINESS);
        assertThat(event.getFields()).containsOnlyKeys("valid", "second");
        assertThat(snapshot).containsEntry("valid", 1).containsEntry("second", 2);
        assertThatThrownBy(() -> snapshot.put("third", 3))
            .isInstanceOf(UnsupportedOperationException.class);

        MutablePlatformLogEvent empty = new MutablePlatformLogEvent(
            "EMPTY", null, LogSeverity.DEBUG, LogCategory.APPLICATION, null);
        assertThat(empty.fieldsSnapshot()).isEmpty();
    }

    @Test
    void immutableEventDefensivelyCopiesAndFiltersFields() {
        LinkedHashMap<String, Object> input = new LinkedHashMap<>();
        input.put("valid", "value");
        input.put(null, "ignored");
        input.put("null-value", null);
        PlatformLogEvent event = new PlatformLogEvent(
            "EVENT", "message", LogSeverity.WARN, LogCategory.INTEGRATION,
            NOW, input);
        input.put("late", "mutation");

        assertThat(event.getEventName()).isEqualTo("EVENT");
        assertThat(event.getMessage()).isEqualTo("message");
        assertThat(event.getSeverity()).isEqualTo(LogSeverity.WARN);
        assertThat(event.getCategory()).isEqualTo(LogCategory.INTEGRATION);
        assertThat(event.getTimestamp()).isEqualTo(NOW);
        assertThat(event.getFields()).containsOnlyKeys("valid");
        assertThatThrownBy(() -> event.getFields().put("late", "mutation"))
            .isInstanceOf(UnsupportedOperationException.class);

        PlatformLogEvent empty = new PlatformLogEvent(
            "EMPTY", null, LogSeverity.TRACE, LogCategory.CACHE, NOW, null);
        assertThat(empty.getFields()).isEmpty();
    }

    @Test
    void logFieldRequiresNameAndRetainsTypedValue() {
        PlatformLogField field = new PlatformLogField("attempt", 3);
        assertThat(field.getName()).isEqualTo("attempt");
        assertThat(field.getValue()).isEqualTo(3);
        assertThatThrownBy(() -> new PlatformLogField(null, "value"))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("name");
    }

    @Test
    void baseAuditEventExposesAllFields() {
        Duration duration = Duration.ofMillis(12);
        LoggingAuditEvent event = new LoggingAuditEvent(
            "TYPE", NOW, "OPERATION", "FAILED", "provider", "algorithm",
            4, 2, duration, "ErrorType", "trace", "request");

        assertThat(event.getEventType()).isEqualTo("TYPE");
        assertThat(event.getTimestamp()).isEqualTo(NOW);
        assertThat(event.getOperation()).isEqualTo("OPERATION");
        assertThat(event.getOutcome()).isEqualTo("FAILED");
        assertThat(event.getProvider()).isEqualTo("provider");
        assertThat(event.getAlgorithm()).isEqualTo("algorithm");
        assertThat(event.getMaskedFieldCount()).isEqualTo(4);
        assertThat(event.getRemovedFieldCount()).isEqualTo(2);
        assertThat(event.getDuration()).isEqualTo(duration);
        assertThat(event.getErrorType()).isEqualTo("ErrorType");
        assertThat(event.getTraceId()).isEqualTo("trace");
        assertThat(event.getRequestId()).isEqualTo("request");
    }

    @Test
    void specializedAuditEventsSetStableTypesAndExpectedMetadata() {
        CryptoOperationEvent crypto = new CryptoOperationEvent(
            NOW, "ENCRYPT", "FAILED", "JCA", "AES_GCM_256",
            Duration.ofMillis(5), "CryptoError", "trace", "request");
        assertThat(crypto.getEventType()).isEqualTo("CRYPTO_OPERATION");
        assertThat(crypto.getProvider()).isEqualTo("JCA");
        assertThat(crypto.getAlgorithm()).isEqualTo("AES_GCM_256");
        assertThat(crypto.getMaskedFieldCount()).isZero();
        assertThat(crypto.getRemovedFieldCount()).isZero();

        MaskingAppliedEvent masking =
            new MaskingAppliedEvent(NOW, 7, 3, Duration.ofMillis(2));
        assertThat(masking.getEventType()).isEqualTo("MASKING_APPLIED");
        assertThat(masking.getOperation()).isEqualTo("MASK");
        assertThat(masking.getOutcome()).isEqualTo("SUCCESS");
        assertThat(masking.getMaskedFieldCount()).isEqualTo(7);
        assertThat(masking.getRemovedFieldCount()).isEqualTo(3);

        PlatformAuditLogEvent audit =
            new PlatformAuditLogEvent(NOW, "UPDATE", "SUCCESS", "trace", "request");
        assertThat(audit.getEventType()).isEqualTo("PLATFORM_AUDIT_LOG");
        assertThat(audit.getErrorType()).isNull();

        PlatformSecurityLogEvent security = new PlatformSecurityLogEvent(
            NOW, "LOGIN", "FAILED", "Denied", "trace", "request");
        assertThat(security.getEventType()).isEqualTo("PLATFORM_SECURITY_LOG");
        assertThat(security.getErrorType()).isEqualTo("Denied");
    }

    @Test
    void springPublisherForwardsSameEventInstance() {
        AtomicReference<Object> received = new AtomicReference<>();
        SpringLoggingAuditEventPublisher publisher =
            new SpringLoggingAuditEventPublisher(received::set);
        LoggingAuditEvent event = new PlatformAuditLogEvent(
            NOW, "READ", "SUCCESS", "trace", "request");

        publisher.publish(event);

        assertThat(received).hasValue(event);
    }
}
