package com.company.platform.queue.observability.metrics;

import com.company.platform.queue.domain.model.QueueProviderType;

import java.time.Duration;

public interface QueueMetrics {
    void recordPublish(
        QueueProviderType provider, String broker, String destination,
        String outcome, Duration duration);
    void recordRetry(
        QueueProviderType provider, String broker, String destination,
        String outcome);
    void recordDuplicate(QueueProviderType provider, String destination);
}
