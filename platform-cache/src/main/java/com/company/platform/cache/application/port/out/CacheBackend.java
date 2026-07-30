package com.company.platform.cache.application.port.out;

import java.time.Duration;
import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * Provider-neutral secondary port implemented by one physical named-cache backend.
 *
 * <p>The application layer must canonicalize and validate keys before invoking this
 * port. Implementations must not derive key identity from {@link Object#hashCode()}.</p>
 */
public interface CacheBackend {

    Optional<BackendCacheEntry> get(String key);

    void put(String key, Object value, Duration ttl);

    default void putEntry(String key, BackendCacheEntry entry, Duration ttl) {
        put(key, entry.getValue(), ttl);
    }

    boolean putIfAbsent(String key, Object value, Duration ttl);

    boolean evict(String key);

    BackendClearResult clear();

    String namespaceToken();

    long estimatedSize();

    long increment(String key, long delta, Duration ttl);

    boolean compareAndSet(String key, Object expectedValue, Object newValue);

    boolean compareAndDelete(String key, Object expectedValue);

    BackendUpdateResult updateIfVersion(
        String key, long expectedVersion, Object newValue);

    BackendUpdateResult compute(
        String key, UnaryOperator<Object> updater);
}
