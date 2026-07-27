package com.company.platform.logging.audit.event;

import java.time.Duration;
import java.time.OffsetDateTime;

public final class CryptoOperationEvent extends LoggingAuditEvent {
    public CryptoOperationEvent(
        OffsetDateTime timestamp, String operation, String outcome,
        String provider, String algorithm, Duration duration, String errorType,
        String traceId, String requestId
    ) {
        super("CRYPTO_OPERATION", timestamp, operation, outcome, provider,
            algorithm, 0, 0, duration, errorType, traceId, requestId);
    }
}
