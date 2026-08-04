package com.company.platform.integration.cacheapp.api;

import com.company.platform.integration.cacheapp.service.NamedCacheTestService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/cache/redis")
public class RedisCacheController {
    private static final String CACHE_NAME = "demo-redis";
    private final NamedCacheTestService caches;

    public RedisCacheController(NamedCacheTestService caches) {
        this.caches = caches;
    }

    @GetMapping("/{key}")
    public CacheReadResponse get(@PathVariable @Size(max = 128) String key) {
        return caches.get(CACHE_NAME, key);
    }

    @PutMapping("/{key}")
    public CacheMutationResponse put(
        @PathVariable @Size(max = 128) String key,
        @Valid @RequestBody CacheValueRequest request
    ) {
        return caches.put(CACHE_NAME, "REDIS", key, request.value());
    }

    @DeleteMapping("/{key}")
    public CacheMutationResponse evict(@PathVariable @Size(max = 128) String key) {
        return caches.evict(CACHE_NAME, "REDIS", key);
    }

    @DeleteMapping
    public CacheClearResponse clear() {
        return caches.clear(CACHE_NAME);
    }
}
