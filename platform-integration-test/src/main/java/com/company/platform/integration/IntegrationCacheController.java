package com.company.platform.integration;

import com.company.platform.cache.internal.adapter.redis.RedisCacheBackend;
import com.company.platform.cache.internal.application.port.out.CacheBackend;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/platform/integration/cache")
@RequiredArgsConstructor
public class IntegrationCacheController {

    public static final String CACHE_NAME = "integration-local";
    private Map<String, String> cache = new HashMap<>();
    private final RedisCacheBackend cacheBackend;

    @GetMapping("/cached")
    public Map<String, String> getCachedData() {
        cache = this.mapDataManually();
        return cache;
    }

    private Map<String, String> mapDataManually() {
        Map<String, String> data = new HashMap<>();
        data.put("key1", "value1");
        data.put("key2", "value2");
        data.put("key3", "value3");
        data.put("key4", "value4");
        data.put("key5", "value5");
        data.put("key6", "value6");
        data.put("key7", "value7");
        data.put("key8", "value8");
        data.put("key9", "value9");
        data.put("key10", "value10");
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        cacheBackend.put(CACHE_NAME, cache, Duration.ofMinutes(2)); // Cache for 1 hour
        return data;
    }
}
