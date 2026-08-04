package com.company.platform.integration.cacheapp.service;

import com.company.platform.cache.api.operation.PlatformCacheOperations;
import com.company.platform.cache.domain.result.CacheClearResult;
import com.company.platform.cache.domain.result.CacheResult;
import com.company.platform.integration.cacheapp.api.CacheClearResponse;
import com.company.platform.integration.cacheapp.api.CacheMutationResponse;
import com.company.platform.integration.cacheapp.api.CacheReadResponse;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class NamedCacheTestService {
    private final PlatformCacheOperations cache;

    public NamedCacheTestService(PlatformCacheOperations cache) {
        this.cache = cache;
    }

    public CacheReadResponse get(String cacheName, String key) {
        CacheResult<String> result = cache.getResult(cacheName, key, String.class);
        return new CacheReadResponse(
            cacheName,
            key,
            result.getValue() != null,
            result.getValue(),
            result.getStatus().name(),
            result.getTier().name());
    }

    public CacheMutationResponse put(String cacheName, String tier, String key, String value) {
        cache.put(cacheName, key, value);
        return new CacheMutationResponse(key, "PUT", List.of(tier));
    }

    public CacheMutationResponse evict(String cacheName, String tier, String key) {
        cache.evict(cacheName, key);
        return new CacheMutationResponse(key, "EVICT", List.of(tier));
    }

    public CacheClearResponse clear(String cacheName) {
        CacheClearResult result = cache.clear(cacheName);
        Long deletedCount = result.getExactDeletedCount().isPresent()
            ? result.getExactDeletedCount().getAsLong()
            : null;
        return new CacheClearResponse(
            cacheName,
            result.isSuccess(),
            result.getStrategy(),
            deletedCount);
    }
}
