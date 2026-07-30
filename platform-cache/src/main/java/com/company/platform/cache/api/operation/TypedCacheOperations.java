package com.company.platform.cache.api.operation;

import com.company.platform.cache.domain.result.CacheClearResult;

import java.util.Optional;
import java.util.function.Supplier;

public interface TypedCacheOperations<K, V> {
    Optional<V> get(K key);

    V getOrLoad(K key, Supplier<V> loader);

    void put(K key, V value);

    boolean putIfAbsent(K key, V value);

    boolean exists(K key);

    boolean evict(K key);

    CacheClearResult clear();
}
