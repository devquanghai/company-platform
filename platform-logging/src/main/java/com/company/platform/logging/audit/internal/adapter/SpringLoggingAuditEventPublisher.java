package com.company.platform.logging.audit.internal.adapter;

import com.company.platform.logging.audit.event.LoggingAuditEvent;
import com.company.platform.logging.audit.publisher.LoggingAuditEventPublisher;
import org.springframework.context.ApplicationEventPublisher;

public final class SpringLoggingAuditEventPublisher
    implements LoggingAuditEventPublisher {
    private final ApplicationEventPublisher publisher;

    public SpringLoggingAuditEventPublisher(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Override public void publish(LoggingAuditEvent event) {
        publisher.publishEvent(event);
    }
}
