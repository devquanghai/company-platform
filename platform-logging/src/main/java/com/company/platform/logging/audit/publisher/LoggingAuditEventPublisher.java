package com.company.platform.logging.audit.publisher;

import com.company.platform.logging.audit.event.LoggingAuditEvent;

@FunctionalInterface
public interface LoggingAuditEventPublisher {
    void publish(LoggingAuditEvent event);
}
