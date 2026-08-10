package com.company.platform.queue.observability.internal.adapter.metrics;

import com.company.platform.queue.domain.model.QueueProviderType;
import com.company.platform.queue.observability.metrics.QueueMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;

import java.time.Duration;
import java.util.List;

public final class MicrometerQueueMetrics implements QueueMetrics {
    private final MeterRegistry registry;

    public MicrometerQueueMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void recordPublish(
        QueueProviderType provider, String broker, String destination,
        String outcome, Duration duration
    ) {
        List<Tag> tags = tags(provider, broker, destination, outcome);
        registry.counter("platform.queue.publish", tags).increment();
        registry.timer("platform.queue.publish.duration", tags).record(duration);
    }

    @Override
    public void recordRetry(
        QueueProviderType provider, String broker, String destination,
        String outcome
    ) {
        registry.counter(
            "platform.queue.retries", tags(provider, broker, destination, outcome))
            .increment();
    }

    @Override
    public void recordDuplicate(QueueProviderType provider, String destination) {
        registry.counter("platform.queue.duplicates",
            "provider", provider.name(), "destination", destination).increment();
    }

    private List<Tag> tags(
        QueueProviderType provider, String broker, String destination, String outcome
    ) {
        return List.of(
            Tag.of("provider", provider.name()),
            Tag.of("broker", broker),
            Tag.of("destination", destination),
            Tag.of("outcome", outcome));
    }
}
