package com.company.platform.cache.api.resilience;

import java.util.function.Supplier;

/**
 * Vendor-neutral extension point for guarding cache I/O.
 */
public interface CacheResilienceExecutor {
    <T> T executeRead(Supplier<T> invocation);

    <T> T executeWrite(Supplier<T> invocation);

    /**
     * Returns whether a guarded cache failure may safely fall through to the
     * source loader.
     */
    default boolean shouldFailOpen(Throwable failure) {
        return false;
    }

    default void executeWrite(Runnable invocation) {
        executeWrite(() -> {
            invocation.run();
            return null;
        });
    }

    static CacheResilienceExecutor none() {
        return new CacheResilienceExecutor() {
            @Override
            public <T> T executeRead(Supplier<T> invocation) {
                return invocation.get();
            }

            @Override
            public <T> T executeWrite(Supplier<T> invocation) {
                return invocation.get();
            }
        };
    }
}
