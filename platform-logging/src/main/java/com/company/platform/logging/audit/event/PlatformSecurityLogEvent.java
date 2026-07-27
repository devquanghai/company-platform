package com.company.platform.logging.audit.event;

import java.time.OffsetDateTime;

public final class PlatformSecurityLogEvent extends LoggingAuditEvent {
    public PlatformSecurityLogEvent(
        OffsetDateTime timestamp, String operation, String outcome,
        String errorType, String traceId, String requestId
    ) {
        super("PLATFORM_SECURITY_LOG", timestamp, operation, outcome,
            null, null, 0, 0, null, errorType, traceId, requestId);
    }
}
