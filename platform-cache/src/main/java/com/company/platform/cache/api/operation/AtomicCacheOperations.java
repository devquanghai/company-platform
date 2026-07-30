package com.company.platform.cache.api.operation;

import com.company.platform.cache.domain.result.AtomicUpdateResult;

import java.util.function.UnaryOperator;

public interface AtomicCacheOperations {
    long increment(String cacheName, Object key, long delta);

    default long decrement(String cacheName, Object key, long delta) {
        if (delta < 0) {
            throw new IllegalArgumentException("delta must not be negative");
        }
        return increment(cacheName, key, -delta);
    }

    boolean compareAndSet(
        String cacheName, Object key, Object expectedValue, Object newValue);

    boolean compareAndDelete(
        String cacheName, Object key, Object expectedValue);

    <T> AtomicUpdateResult<T> update(
        String cacheName, Object key, Class<T> valueType, UnaryOperator<T> updater);
}
