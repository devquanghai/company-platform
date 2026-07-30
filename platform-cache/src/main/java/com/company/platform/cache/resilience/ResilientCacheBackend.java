package com.company.platform.cache.resilience;

import com.company.platform.cache.application.port.out.BackendCacheEntry;
import com.company.platform.cache.application.port.out.BackendClearResult;
import com.company.platform.cache.application.port.out.BackendUpdateResult;
import com.company.platform.cache.application.port.out.CacheBackend;

import java.time.Duration;
import java.util.Optional;
import java.util.function.UnaryOperator;

public final class ResilientCacheBackend implements CacheBackend {
    private final CacheBackend delegate;
    private final CacheResilienceExecutor resilience;

    public ResilientCacheBackend(
        CacheBackend delegate, CacheResilienceExecutor resilience
    ) {
        this.delegate = java.util.Objects.requireNonNull(delegate, "delegate");
        this.resilience = java.util.Objects.requireNonNull(resilience, "resilience");
    }

    @Override
    public Optional<BackendCacheEntry> get(String key) {
        return resilience.execute(() -> delegate.get(key), true);
    }

    @Override
    public void put(String key, Object value, Duration ttl) {
        resilience.execute(() -> delegate.put(key, value, ttl), false);
    }

    @Override
    public void putEntry(String key, BackendCacheEntry entry, Duration ttl) {
        resilience.execute(() -> delegate.putEntry(key, entry, ttl), false);
    }

    @Override
    public boolean putIfAbsent(String key, Object value, Duration ttl) {
        return resilience.execute(
            () -> delegate.putIfAbsent(key, value, ttl), false);
    }

    @Override
    public boolean evict(String key) {
        return resilience.execute(() -> delegate.evict(key), false);
    }

    @Override
    public BackendClearResult clear() {
        return resilience.execute(delegate::clear, false);
    }

    @Override
    public String namespaceToken() {
        return resilience.execute(delegate::namespaceToken, true);
    }

    @Override
    public long estimatedSize() {
        return delegate.estimatedSize();
    }

    @Override
    public long increment(String key, long delta, Duration ttl) {
        return resilience.execute(
            () -> delegate.increment(key, delta, ttl), false);
    }

    @Override
    public boolean compareAndSet(
        String key, Object expectedValue, Object newValue
    ) {
        return resilience.execute(
            () -> delegate.compareAndSet(key, expectedValue, newValue), false);
    }

    @Override
    public boolean compareAndDelete(String key, Object expectedValue) {
        return resilience.execute(
            () -> delegate.compareAndDelete(key, expectedValue), false);
    }

    @Override
    public BackendUpdateResult updateIfVersion(
        String key, long expectedVersion, Object newValue
    ) {
        return resilience.execute(
            () -> delegate.updateIfVersion(key, expectedVersion, newValue), false);
    }

    @Override
    public BackendUpdateResult compute(
        String key, UnaryOperator<Object> updater
    ) {
        return resilience.execute(() -> delegate.compute(key, updater), false);
    }
}
