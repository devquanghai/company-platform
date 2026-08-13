package com.company.platform.cache.internal.springcache;

import com.company.platform.cache.api.model.CacheType;
import com.company.platform.cache.api.operation.PlatformCacheOperations;
import com.company.platform.cache.autoconfigure.properties.PlatformCacheProperties;
import com.company.platform.cache.domain.exception.PlatformCacheOperationException;
import com.company.platform.cache.domain.model.CacheProviderType;
import com.company.platform.cache.domain.model.CacheResultStatus;
import com.company.platform.cache.domain.model.CacheTier;
import com.company.platform.cache.domain.result.CacheClearResult;
import com.company.platform.cache.domain.result.CacheResult;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.lang.reflect.Type;
import java.time.Duration;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

public final class SpringCachePlatformCacheOperations implements PlatformCacheOperations {
    private final CacheManager cacheManager;
    private final CacheProviderType provider;

    public SpringCachePlatformCacheOperations(
        CacheManager cacheManager,
        PlatformCacheProperties properties
    ) {
        this.cacheManager = Objects.requireNonNull(cacheManager, "cacheManager");
        this.provider = CacheProviderType.valueOf(properties.getProvider().name());
    }

    @Override
    public <K, V> Optional<V> get(String cacheName, K key, Class<V> valueType) {
        try {
            return Optional.ofNullable(cache(cacheName).get(requireKey(key), valueType));
        } catch (RuntimeException exception) {
            throw operationFailure("get", exception);
        }
    }

    @Override
    public <K, V> Optional<V> get(String cacheName, K key, CacheType<V> valueType) {
        Objects.requireNonNull(valueType, "valueType");
        Type type = valueType.getType();
        if (!(type instanceof Class<?> valueClass)) {
            throw new IllegalArgumentException("CacheType must contain a Class");
        }
        @SuppressWarnings("unchecked")
        Class<V> castType = (Class<V>) valueClass;
        return get(cacheName, key, castType);
    }

    @Override
    public <K, V> V getOrLoad(
        String cacheName,
        K key,
        Class<V> valueType,
        Supplier<V> loader
    ) {
        Objects.requireNonNull(valueType, "valueType");
        Objects.requireNonNull(loader, "loader");
        try {
            return valueType.cast(cache(cacheName).get(requireKey(key), loader::get));
        } catch (RuntimeException exception) {
            throw operationFailure("get-or-load", exception);
        }
    }

    @Override
    public <K, V> V getOrLoad(
        String cacheName,
        K key,
        CacheType<V> valueType,
        Supplier<V> loader
    ) {
        Objects.requireNonNull(valueType, "valueType");
        Type type = valueType.getType();
        if (!(type instanceof Class<?> valueClass)) {
            throw new IllegalArgumentException("CacheType must contain a Class");
        }
        @SuppressWarnings("unchecked")
        Class<V> castType = (Class<V>) valueClass;
        return getOrLoad(cacheName, key, castType, loader);
    }

    @Override
    public <K, V> CacheResult<V> getResult(
        String cacheName,
        K key,
        Class<V> valueType
    ) {
        long started = System.nanoTime();
        Optional<V> value = get(cacheName, key, valueType);
        return CacheResult.<V>builder()
            .status(value.isPresent() ? CacheResultStatus.HIT : CacheResultStatus.MISS)
            .value(value.orElse(null))
            .cacheName(cacheName)
            .provider(provider)
            .tier(CacheTier.NONE)
            .stale(false)
            .latency(Duration.ofNanos(System.nanoTime() - started))
            .build();
    }

    @Override
    public <K, V> void put(String cacheName, K key, V value) {
        try {
            cache(cacheName).put(requireKey(key), value);
        } catch (RuntimeException exception) {
            throw operationFailure("put", exception);
        }
    }

    @Override
    public <K, V> boolean putIfAbsent(String cacheName, K key, V value) {
        try {
            return cache(cacheName).putIfAbsent(requireKey(key), value) == null;
        } catch (RuntimeException exception) {
            throw operationFailure("put-if-absent", exception);
        }
    }

    @Override
    public <K> boolean exists(String cacheName, K key) {
        try {
            return cache(cacheName).get(requireKey(key)) != null;
        } catch (RuntimeException exception) {
            throw operationFailure("exists", exception);
        }
    }

    @Override
    public <K> boolean evict(String cacheName, K key) {
        try {
            return cache(cacheName).evictIfPresent(requireKey(key));
        } catch (RuntimeException exception) {
            throw operationFailure("evict", exception);
        }
    }

    @Override
    public CacheClearResult clear(String cacheName) {
        try {
            cache(cacheName).clear();
            return CacheClearResult.builder()
                .strategy(provider == CacheProviderType.REDIS
                    ? "REDIS_SCAN_CLEAR_BEST_EFFORT"
                    : "SPRING_CACHE_CLEAR")
                .success(true)
                .build();
        } catch (RuntimeException exception) {
            throw operationFailure("clear", exception);
        }
    }

    @Override
    public <K, V> Map<K, V> getAll(
        String cacheName,
        Collection<K> keys,
        Class<V> valueType
    ) {
        Objects.requireNonNull(keys, "keys");
        Map<K, V> values = new LinkedHashMap<>();
        for (K key : keys) {
            get(cacheName, key, valueType).ifPresent(value -> values.put(key, value));
        }
        return values;
    }

    @Override
    public <K, V> void putAll(String cacheName, Map<K, V> entries) {
        Objects.requireNonNull(entries, "entries");
        entries.forEach((key, value) -> put(cacheName, key, value));
    }

    private Cache cache(String cacheName) {
        Objects.requireNonNull(cacheName, "cacheName");
        Cache cache = cacheManager.getCache(cacheName);
        if (cache == null) {
            throw new IllegalArgumentException("Unknown cache name");
        }
        return cache;
    }

    private Object requireKey(Object key) {
        return Objects.requireNonNull(key, "key");
    }

    private PlatformCacheOperationException operationFailure(
        String operation,
        RuntimeException cause
    ) {
        return new PlatformCacheOperationException(
            "CACHE.OPERATION.FAILED", "Cache " + operation + " operation failed", cause);
    }
}
