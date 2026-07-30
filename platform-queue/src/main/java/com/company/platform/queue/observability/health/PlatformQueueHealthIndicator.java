package com.company.platform.queue.observability.health;

import com.company.platform.queue.application.registry.QueueBrokerRegistry;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

public final class PlatformQueueHealthIndicator implements HealthIndicator {
    private final QueueBrokerRegistry brokers;

    public PlatformQueueHealthIndicator(QueueBrokerRegistry brokers) {
        this.brokers = brokers;
    }

    @Override
    public Health health() {
        long enabled = brokers.entries().values().stream()
            .filter(broker -> broker.isEnabled()).count();
        return Health.up()
            .withDetail("statusCode", "QUEUE_READY")
            .withDetail("enabledBrokers", enabled)
            .build();
    }
}
