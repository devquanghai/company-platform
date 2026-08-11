package com.company.platform.integration.cache.service;

import com.company.platform.integration.cache.dto.request.CacheRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@Slf4j
public class CacheService {
    private static final String CACHE_KEY = "test-cache-caffeine";
    private final AtomicInteger loads = new AtomicInteger();

    @Cacheable(cacheNames = CACHE_KEY, key = "#key")
    public List<CacheRequest> get(String key) throws InterruptedException {
        String test = "cache_test_" + key + "_" + loads.incrementAndGet();
        Thread.sleep(3000);
        return List.of(new CacheRequest(key, test));
    }

    @CacheEvict(cacheNames = CACHE_KEY, key = "#key")
    public void evict(String key) {
        log.info("Evicting cache for key: {}", key);
    }



}
