package com.company.platform.cache.application.service;

import com.company.platform.cache.api.operation.PlatformCacheOperations;
import com.company.platform.cache.api.operation.TypedCacheFactory;
import com.company.platform.cache.api.operation.TypedCacheOperations;
import com.company.platform.cache.domain.result.CacheClearResult;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

public final class DefaultTypedCacheFactory implements TypedCacheFactory {
    private final PlatformCacheOperations operations;

    public DefaultTypedCacheFactory(PlatformCacheOperations operations) {
        this.operations = Objects.requireNonNull(operations, "operations");
    }

    @Override
    public <K, V> TypedCacheOperations<K, V> getCache(
        String cacheName, Class<K> keyType, Class<V> valueType
    ) {
        Objects.requireNonNull(cacheName, "cacheName");
        Objects.requireNonNull(keyType, "keyType");
        Objects.requireNonNull(valueType, "valueType");
        return new DefaultTypedCacheOperations<>(
            operations, cacheName, keyType, valueType);
    }

    private static final class DefaultTypedCacheOperations<K, V>
        implements TypedCacheOperations<K, V> {
        private final PlatformCacheOperations operations;
        private final String cacheName;
        private final Class<K> keyType;
        private final Class<V> valueType;

        private DefaultTypedCacheOperations(
            PlatformCacheOperations operations,
            String cacheName,
            Class<K> keyType,
            Class<V> valueType
        ) {
            this.operations = operations;
            this.cacheName = cacheName;
            this.keyType = keyType;
            this.valueType = valueType;
        }

        @Override
        public Optional<V> get(K key) {
            return operations.get(cacheName, requireKey(key), valueType);
        }

        @Override
        public V getOrLoad(K key, Supplier<V> loader) {
            return operations.getOrLoad(
                cacheName, requireKey(key), valueType, loader);
        }

        @Override
        public void put(K key, V value) {
            operations.put(cacheName, requireKey(key), value);
        }

        @Override
        public boolean putIfAbsent(K key, V value) {
            return operations.putIfAbsent(cacheName, requireKey(key), value);
        }

        @Override
        public boolean exists(K key) {
            return operations.exists(cacheName, requireKey(key));
        }

        @Override
        public boolean evict(K key) {
            return operations.evict(cacheName, requireKey(key));
        }

        @Override
        public CacheClearResult clear() {
            return operations.clear(cacheName);
        }

        private K requireKey(K key) {
            return keyType.cast(Objects.requireNonNull(key, "key"));
        }
    }
}
