package com.company.platform.integration;

import com.company.platform.cache.api.annotation.PlatformCacheEvict;
import com.company.platform.cache.api.annotation.PlatformCacheable;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicInteger;

@Service
@Profile("integration-e2e")
public class IntegrationCacheScenarioService {
    private final AtomicInteger loads = new AtomicInteger();

    @PlatformCacheable(cacheNames = "integration-local", key = "#key")
    public String cached(String key) {
        return "loaded-" + key + "-" + loads.incrementAndGet();
    }

    @PlatformCacheEvict(cacheNames = "integration-local", key = "#key")
    public void evict(String key) {
        // Eviction is performed by the platform Spring Cache bridge.
    }

    public int loadCount() {
        return loads.get();
    }
}
