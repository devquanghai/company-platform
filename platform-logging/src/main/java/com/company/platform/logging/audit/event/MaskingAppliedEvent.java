package com.company.platform.logging.audit.event;

import java.time.Duration;
import java.time.OffsetDateTime;

public final class MaskingAppliedEvent extends LoggingAuditEvent {
    public MaskingAppliedEvent(
        OffsetDateTime timestamp, int masked, int removed, Duration duration
    ) {
        super("MASKING_APPLIED", timestamp, "MASK", "SUCCESS",
            null, null, masked, removed, duration, null, null, null);
    }
}
