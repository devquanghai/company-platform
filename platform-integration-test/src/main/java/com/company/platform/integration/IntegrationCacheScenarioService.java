package com.company.platform.integration;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicInteger;

@Service
@Profile("integration-e2e")
public class IntegrationCacheScenarioService {
    private final AtomicInteger loads = new AtomicInteger();

    @Cacheable(cacheNames = "integration-local", key = "#key")
    public String cached(String key) {
        return "loaded-" + key + "-" + loads.incrementAndGet();
    }

    @CacheEvict(cacheNames = "integration-local", key = "#key")
    public void evict(String key) {
        // Eviction is performed by the platform Spring Cache bridge.
    }

    public int loadCount() {
        return loads.get();
    }
}
