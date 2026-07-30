package com.company.platform.cache.adapter.springcache;

import com.company.platform.cache.api.operation.PlatformCacheOperations;
import com.company.platform.cache.application.resolver.CacheDefinitionRegistry;
import com.company.platform.cache.domain.model.CacheResultStatus;
import com.company.platform.cache.domain.result.CacheResult;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.support.SimpleValueWrapper;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;

public final class PlatformSpringCacheManager implements CacheManager {
    private final Map<String, Cache> caches;

    public PlatformSpringCacheManager(
        PlatformCacheOperations operations,
        CacheDefinitionRegistry definitions
    ) {
        Objects.requireNonNull(operations, "operations");
        Objects.requireNonNull(definitions, "definitions");
        Map<String, Cache> resolved = new LinkedHashMap<>();
        definitions.getCaches().forEach((name, definition) ->
            resolved.put(name, new PlatformSpringCache(name, operations)));
        caches = Collections.unmodifiableMap(resolved);
    }

    @Override
    public Cache getCache(String name) {
        return caches.get(name);
    }

    @Override
    public Collection<String> getCacheNames() {
        return caches.keySet();
    }

    private static final class PlatformSpringCache implements Cache {
        private final String name;
        private final PlatformCacheOperations operations;

        private PlatformSpringCache(
            String name, PlatformCacheOperations operations
        ) {
            this.name = name;
            this.operations = operations;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public Object getNativeCache() {
            return operations;
        }

        @Override
        public ValueWrapper get(Object key) {
            CacheResult<Object> result = operations.getResult(name, key, Object.class);
            return isHit(result.getStatus())
                ? new SimpleValueWrapper(result.getValue()) : null;
        }

        @Override
        public <T> T get(Object key, Class<T> type) {
            CacheResult<T> result = operations.getResult(name, key, type);
            return isHit(result.getStatus()) ? result.getValue() : null;
        }

        @Override
        public <T> T get(Object key, Callable<T> valueLoader) {
            return operations.getOrLoad(name, key, castClass(), () -> {
                try {
                    return valueLoader.call();
                } catch (Exception exception) {
                    throw new ValueRetrievalException(key, valueLoader, exception);
                }
            });
        }

        @Override
        public void put(Object key, Object value) {
            operations.put(name, key, value);
        }

        @Override
        public ValueWrapper putIfAbsent(Object key, Object value) {
            if (operations.putIfAbsent(name, key, value)) {
                return null;
            }
            return get(key);
        }

        @Override
        public void evict(Object key) {
            operations.evict(name, key);
        }

        @Override
        public boolean evictIfPresent(Object key) {
            return operations.evict(name, key);
        }

        @Override
        public void clear() {
            operations.clear(name);
        }

        @Override
        public boolean invalidate() {
            return operations.clear(name).isSuccess();
        }

        private boolean isHit(CacheResultStatus status) {
            return status == CacheResultStatus.HIT
                || status == CacheResultStatus.HIT_FALLBACK;
        }

        @SuppressWarnings("unchecked")
        private <T> Class<T> castClass() {
            return (Class<T>) Object.class;
        }
    }
}
