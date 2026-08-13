package com.company.platform.cache.api.observability;

import java.util.function.Supplier;

/**
 * Vendor-neutral extension point for observing cache I/O.
 */
public interface CacheOperationObservability {
    <T> T observe(String operation, String provider, Supplier<T> invocation);

    default void observe(String operation, String provider, Runnable invocation) {
        observe(operation, provider, () -> {
            invocation.run();
            return null;
        });
    }

    static CacheOperationObservability none() {
        return new CacheOperationObservability() {
            @Override
            public <T> T observe(
                String operation,
                String provider,
                Supplier<T> invocation
            ) {
                return invocation.get();
            }
        };
    }
}
