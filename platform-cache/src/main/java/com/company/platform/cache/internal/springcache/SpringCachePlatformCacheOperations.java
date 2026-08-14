package com.company.platform.cache.internal.springcache;

import com.company.platform.cache.api.model.CacheType;
import com.company.platform.cache.api.observability.CacheOperationObservability;
import com.company.platform.cache.api.operation.PlatformCacheOperations;
import com.company.platform.cache.api.resilience.CacheResilienceExecutor;
import com.company.platform.cache.autoconfigure.properties.PlatformCacheProperties;
import com.company.platform.cache.domain.exception.PlatformCacheOperationException;
import com.company.platform.cache.domain.model.CacheProviderType;
import com.company.platform.cache.domain.model.CacheResultStatus;
import com.company.platform.cache.domain.model.CacheTier;
import com.company.platform.cache.domain.result.CacheClearResult;
import com.company.platform.cache.domain.result.CacheResult;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Type;
import java.time.Duration;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

public final class SpringCachePlatformCacheOperations implements PlatformCacheOperations {
    private static final Logger LOG = LoggerFactory.getLogger("PLATFORM_CACHE");
    private static final int MAX_IN_FLIGHT_LOADS = 1_024;
    private final CacheManager cacheManager;
    private final CacheProviderType provider;
    private final CacheResilienceExecutor resilience;
    private final CacheOperationObservability observability;
    private final Map<LoadKey, CompletableFuture<Object>> inFlightLoads =
        new ConcurrentHashMap<>();
    private final Semaphore loadPermits = new Semaphore(MAX_IN_FLIGHT_LOADS);

    public SpringCachePlatformCacheOperations(
        CacheManager cacheManager,
        PlatformCacheProperties properties,
        CacheResilienceExecutor resilience,
        CacheOperationObservability observability
    ) {
        this.cacheManager = Objects.requireNonNull(cacheManager, "cacheManager");
        this.provider = CacheProviderType.valueOf(properties.getProvider().name());
        this.resilience = Objects.requireNonNull(resilience, "resilience");
        this.observability = Objects.requireNonNull(observability, "observability");
    }

    @Override
    public <K, V> Optional<V> get(String cacheName, K key, Class<V> valueType) {
        try {
            Cache cache = cache(cacheName);
            Object cacheKey = requireKey(key);
            return observe("get", () -> Optional.ofNullable(
                resilience.executeRead(() -> cache.get(cacheKey, valueType))));
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
        Cache cache = cache(cacheName);
        Object cacheKey = requireKey(key);

        Cache.ValueWrapper cached;
        try {
            cached = observe("get", () -> resilience.executeRead(
                () -> cache.get(cacheKey)));
        } catch (RuntimeException exception) {
            if (!isCacheInfrastructureFailure(exception)) {
                throw exception;
            }
            cached = null;
        }
        if (cached != null) {
            return valueType.cast(cached.get());
        }
        return loadOnce(cache, cacheName, cacheKey, valueType, loader);
    }

    private <V> V loadOnce(
        Cache cache,
        String cacheName,
        Object cacheKey,
        Class<V> valueType,
        Supplier<V> loader
    ) {
        LoadKey loadKey = new LoadKey(cacheName, cacheKey);
        CompletableFuture<Object> existing = inFlightLoads.get(loadKey);
        if (existing != null) {
            return awaitLoad(existing, valueType);
        }
        if (!loadPermits.tryAcquire()) {
            throw new PlatformCacheOperationException(
                "CACHE.LOAD.CAPACITY.EXCEEDED",
                "Too many cache source loads are already in flight",
                null);
        }
        CompletableFuture<Object> created = new CompletableFuture<>();
        existing = inFlightLoads.putIfAbsent(
            loadKey, created);
        if (existing != null) {
            loadPermits.release();
            return awaitLoad(existing, valueType);
        }
        try {
            V loaded = loader.get();
            V published = publishLoaded(cache, cacheKey, valueType, loaded);
            created.complete(published);
            return published;
        } catch (RuntimeException | Error failure) {
            created.completeExceptionally(failure);
            throw failure;
        } finally {
            inFlightLoads.remove(loadKey, created);
            loadPermits.release();
        }
    }

    private <V> V publishLoaded(
        Cache cache,
        Object cacheKey,
        Class<V> valueType,
        V loaded
    ) {
        if (loaded == null) {
            return null;
        }
        try {
            Cache.ValueWrapper winner = observe("put_if_absent",
                () -> resilience.executeWrite(
                    () -> cache.putIfAbsent(cacheKey, loaded)));
            return winner == null ? loaded : valueType.cast(winner.get());
        } catch (RuntimeException exception) {
            if (!isCacheInfrastructureFailure(exception)) {
                throw exception;
            }
            return loaded;
        }
    }

    private <V> V awaitLoad(
        CompletableFuture<Object> inFlight,
        Class<V> valueType
    ) {
        try {
            return valueType.cast(inFlight.join());
        } catch (CompletionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw exception;
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
            Cache cache = cache(cacheName);
            Object cacheKey = requireKey(key);
            observe("put", () -> resilience.executeWrite(
                () -> cache.put(cacheKey, value)));
        } catch (RuntimeException exception) {
            throw operationFailure("put", exception);
        }
    }

    @Override
    public <K, V> boolean putIfAbsent(String cacheName, K key, V value) {
        try {
            Cache cache = cache(cacheName);
            Object cacheKey = requireKey(key);
            return observe("put_if_absent", () -> resilience.executeWrite(
                () -> cache.putIfAbsent(cacheKey, value) == null));
        } catch (RuntimeException exception) {
            throw operationFailure("put-if-absent", exception);
        }
    }

    @Override
    public <K> boolean exists(String cacheName, K key) {
        try {
            Cache cache = cache(cacheName);
            Object cacheKey = requireKey(key);
            return observe("exists", () -> resilience.executeRead(
                () -> cache.get(cacheKey) != null));
        } catch (RuntimeException exception) {
            throw operationFailure("exists", exception);
        }
    }

    @Override
    public <K> boolean evict(String cacheName, K key) {
        try {
            Cache cache = cache(cacheName);
            Object cacheKey = requireKey(key);
            return observe("evict", () -> resilience.executeWrite(
                () -> cache.evictIfPresent(cacheKey)));
        } catch (RuntimeException exception) {
            throw operationFailure("evict", exception);
        }
    }

    @Override
    public CacheClearResult clear(String cacheName) {
        try {
            Cache cache = cache(cacheName);
            return observe("clear", () -> resilience.executeWrite(() -> {
                cache.clear();
                return CacheClearResult.builder()
                    .strategy(provider == CacheProviderType.REDIS
                        ? "REDIS_SCAN_CLEAR_BEST_EFFORT"
                        : "SPRING_CACHE_CLEAR")
                    .success(true)
                    .build();
            }));
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

    private <T> T observe(String operation, Supplier<T> invocation) {
        long started = System.nanoTime();
        LOG.trace("cache_operation_started operation={} provider={}",
            operation, provider);
        try {
            T result = observability.observe(operation, provider.name(), invocation);
            LOG.debug("cache_operation_finished operation={} provider={} outcome=SUCCESS duration_ns={}",
                operation, provider, System.nanoTime() - started);
            return result;
        } catch (RuntimeException failure) {
            LOG.debug("cache_operation_finished operation={} provider={} outcome=FAILED duration_ns={} error_type={}",
                operation, provider, System.nanoTime() - started,
                failure.getClass().getSimpleName());
            throw failure;
        }
    }

    private void observe(String operation, Runnable invocation) {
        observe(operation, () -> {
            invocation.run();
            return null;
        });
    }

    private boolean isCacheInfrastructureFailure(Throwable failure) {
        return resilience.shouldFailOpen(failure);
    }

    private record LoadKey(String cacheName, Object key) {
    }

    private PlatformCacheOperationException operationFailure(
        String operation,
        RuntimeException cause
    ) {
        return new PlatformCacheOperationException(
            "CACHE.OPERATION.FAILED", "Cache " + operation + " operation failed", cause);
    }
}
