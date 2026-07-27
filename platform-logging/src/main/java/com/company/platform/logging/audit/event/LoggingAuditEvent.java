package com.company.platform.logging.audit.event;

import lombok.Getter;

import java.time.Duration;
import java.time.OffsetDateTime;

@Getter
public class LoggingAuditEvent {
    private final String eventType;
    private final OffsetDateTime timestamp;
    private final String operation;
    private final String outcome;
    private final String provider;
    private final String algorithm;
    private final int maskedFieldCount;
    private final int removedFieldCount;
    private final Duration duration;
    private final String errorType;
    private final String traceId;
    private final String requestId;

    public LoggingAuditEvent(
        String eventType, OffsetDateTime timestamp, String operation,
        String outcome, String provider, String algorithm,
        int maskedFieldCount, int removedFieldCount, Duration duration,
        String errorType, String traceId, String requestId
    ) {
        this.eventType = eventType;
        this.timestamp = timestamp;
        this.operation = operation;
        this.outcome = outcome;
        this.provider = provider;
        this.algorithm = algorithm;
        this.maskedFieldCount = maskedFieldCount;
        this.removedFieldCount = removedFieldCount;
        this.duration = duration;
        this.errorType = errorType;
        this.traceId = traceId;
        this.requestId = requestId;
    }
}
