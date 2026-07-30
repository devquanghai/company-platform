package com.company.platform.queue.observability.event;

@FunctionalInterface
public interface QueueAuditEventPublisher {
    void publish(QueueAuditEvent event);
}
