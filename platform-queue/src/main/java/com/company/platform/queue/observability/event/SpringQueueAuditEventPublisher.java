package com.company.platform.queue.observability.event;

import org.springframework.context.ApplicationEventPublisher;

import java.util.Objects;

public final class SpringQueueAuditEventPublisher
    implements QueueAuditEventPublisher {

    private final ApplicationEventPublisher publisher;
    private final ThreadLocal<Boolean> publishing =
        ThreadLocal.withInitial(() -> false);

    public SpringQueueAuditEventPublisher(ApplicationEventPublisher publisher) {
        this.publisher = Objects.requireNonNull(publisher, "publisher");
    }

    @Override
    public void publish(QueueAuditEvent event) {
        if (publishing.get()) {
            return;
        }
        publishing.set(true);
        try {
            publisher.publishEvent(event);
        } catch (RuntimeException ignored) {
            // Audit defaults to fail-open. The event never contains payloads.
        } finally {
            publishing.remove();
        }
    }
}
