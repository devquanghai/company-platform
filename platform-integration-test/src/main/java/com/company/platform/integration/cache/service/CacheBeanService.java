package com.company.platform.integration.cache.service;

import com.company.platform.cache.api.model.CacheType;
import com.company.platform.cache.api.operation.PlatformCacheOperations;
import com.company.platform.integration.cache.dto.request.CacheRequest;
import com.company.platform.integration.cache.dto.response.VersionedCacheResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class CacheBeanService {
    private final PlatformCacheOperations cache;

    private static final String CACHE_KEY = "cache-test";
    private static final String OPTIMISTIC_CACHE = "test-cache-optimistic";
    private static final String OPTIMISTIC_KEY_PREFIX = "optimistic:";

    public List<CacheRequest> get(String key) {
        return cache.getOrLoad(CACHE_KEY, key, CacheType.of(List.class), () -> {
            try {
                Thread.sleep(3000);
                return List.of(new CacheRequest(key, "cache_test_" + key));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        });
    }

    public void evict(String key) {
        log.info("Evicting cache for key: {}", key);
        cache.evict(CACHE_KEY, key);
    }

    public VersionedCacheResponse initializeVersioned(
        String key, String value
    ) {
        String cacheKey = optimisticKey(key);
        CacheRequest createdValue = new CacheRequest(key, value);
        boolean created = cache.putIfAbsent(
            OPTIMISTIC_CACHE, cacheKey, createdValue);
        return created ? new VersionedCacheResponse(1L, createdValue) : null;
    }

    public String distributedLockName(String key) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(optimisticKey(key).getBytes(StandardCharsets.UTF_8));
            return "cache-bean:test-cache-optimistic:"
                + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String optimisticKey(String key) {
        return OPTIMISTIC_KEY_PREFIX + key;
    }

}
