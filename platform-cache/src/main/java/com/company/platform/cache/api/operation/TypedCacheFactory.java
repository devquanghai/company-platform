package com.company.platform.cache.api.operation;

public interface TypedCacheFactory {
    <K, V> TypedCacheOperations<K, V> getCache(
        String cacheName, Class<K> keyType, Class<V> valueType);
}
