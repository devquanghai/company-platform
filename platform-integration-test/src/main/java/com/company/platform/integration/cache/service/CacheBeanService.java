package com.company.platform.integration.cache.service;

import com.company.platform.cache.api.model.CacheType;
import com.company.platform.cache.api.operation.PlatformCacheOperations;
import com.company.platform.integration.cache.dto.request.CacheRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@Slf4j
@RequiredArgsConstructor
public class CacheBeanService {
    private final PlatformCacheOperations cache;

    private static final String CACHE_KEY = "test-cache";
    public List<CacheRequest> get(String key) throws InterruptedException {
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



}
