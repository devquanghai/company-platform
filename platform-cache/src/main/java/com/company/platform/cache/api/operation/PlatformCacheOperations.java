package com.company.platform.cache.api.operation;

import com.company.platform.cache.api.model.CacheType;
import com.company.platform.cache.domain.result.CacheClearResult;
import com.company.platform.cache.domain.result.CacheResult;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

public interface PlatformCacheOperations {
    <K, V> Optional<V> get(String cacheName, K key, Class<V> valueType);

    <K, V> Optional<V> get(String cacheName, K key, CacheType<V> valueType);

    <K, V> V getOrLoad(
        String cacheName, K key, Class<V> valueType, Supplier<V> loader);

    <K, V> V getOrLoad( String cacheName, K key, CacheType<V> valueType, Supplier<V> loader );

    <K, V> CacheResult<V> getResult(
        String cacheName, K key, Class<V> valueType);

    <K, V> void put(String cacheName, K key, V value);

    <K, V> boolean putIfAbsent(String cacheName, K key, V value);

    <K> boolean exists(String cacheName, K key);

    <K> boolean evict(String cacheName, K key);

    CacheClearResult clear(String cacheName);

    default long evictAll(String cacheName) {
        return clear(cacheName).getExactDeletedCount().orElse(-1L);
    }

    <K, V> Map<K, V> getAll(
        String cacheName, Collection<K> keys, Class<V> valueType);

    <K, V> void putAll(String cacheName, Map<K, V> entries);
}
