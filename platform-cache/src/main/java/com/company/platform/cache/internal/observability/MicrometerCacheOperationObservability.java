package com.company.platform.cache.internal.observability;

import com.company.platform.cache.api.observability.CacheOperationObservability;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;

import java.util.Objects;
import java.util.function.Supplier;

public final class MicrometerCacheOperationObservability
    implements CacheOperationObservability {

    private final ObservationRegistry registry;

    public MicrometerCacheOperationObservability(ObservationRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    @Override
    public <T> T observe(
        String operation,
        String provider,
        Supplier<T> invocation
    ) {
        return Observation.createNotStarted("platform.cache.operation", registry)
            .contextualName("cache " + operation)
            .lowCardinalityKeyValue("cache.operation", operation)
            .lowCardinalityKeyValue("cache.provider", provider)
            .observe(invocation);
    }
}
