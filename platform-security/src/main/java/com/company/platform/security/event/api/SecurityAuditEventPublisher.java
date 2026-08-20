package com.company.platform.security.event.api;

@FunctionalInterface
public interface SecurityAuditEventPublisher {
    void publish(SecurityAuditEvent event);
}
