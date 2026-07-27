package com.company.platform.logging.audit.event;

import java.time.OffsetDateTime;

public final class PlatformAuditLogEvent extends LoggingAuditEvent {
    public PlatformAuditLogEvent(
        OffsetDateTime timestamp, String operation, String outcome,
        String traceId, String requestId
    ) {
        super("PLATFORM_AUDIT_LOG", timestamp, operation, outcome,
            null, null, 0, 0, null, null, traceId, requestId);
    }
}
