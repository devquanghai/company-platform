package com.company.platform.cache.api.operation;

import com.company.platform.cache.domain.result.OptimisticUpdateResult;
import com.company.platform.cache.domain.result.VersionedValue;

import java.util.function.UnaryOperator;

public interface OptimisticCacheOperations {
    <T> VersionedValue<T> getVersioned(
        String cacheName, Object key, Class<T> valueType);

    <T> OptimisticUpdateResult<T> updateIfVersion(
        String cacheName, Object key, long expectedVersion, T newValue);

    <T> OptimisticUpdateResult<T> computeWithRetry(
        String cacheName, Object key, Class<T> valueType,
        int maxAttempts, UnaryOperator<T> updater);
}
