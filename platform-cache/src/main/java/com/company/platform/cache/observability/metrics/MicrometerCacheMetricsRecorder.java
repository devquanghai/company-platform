package com.company.platform.cache.observability.metrics;

import com.company.platform.cache.observability.event.CacheOperationEvent;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

public final class MicrometerCacheMetricsRecorder implements CacheMetricsRecorder {
    private final MeterRegistry registry;

    public MicrometerCacheMetricsRecorder(MeterRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    @Override
    public void record(CacheOperationEvent event) {
        Tags tags = Tags.of(
            "cache", event.getCacheName(),
            "provider", event.getProvider().name(),
            "operation", event.getOperation(),
            "outcome", event.getOutcome().name(),
            "tier", event.getTier().name(),
            "fallback", Boolean.toString(event.isFallback()),
            "stale", Boolean.toString(event.isStale()),
            "error_category", event.getErrorCategory() == null
                ? "none" : event.getErrorCategory());
        registry.counter("platform.cache.operations", tags).increment();
        Timer.builder("platform.cache.operation.duration")
            .tags(tags)
            .register(registry)
            .record(event.getDuration().toNanos(), TimeUnit.NANOSECONDS);
    }
}
