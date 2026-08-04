package com.company.platform.integration.cacheapp.api;

import com.company.platform.integration.cacheapp.service.ThreeLevelCacheService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
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
@RequestMapping("/api/cache/three-level")
public class ThreeLevelCacheController {
    private final ThreeLevelCacheService caches;

    public ThreeLevelCacheController(ThreeLevelCacheService caches) {
        this.caches = caches;
    }

    @GetMapping("/{key}")
    public ThreeLevelCacheReadResponse get(@PathVariable @Size(max = 128) String key) {
        return caches.get(key);
    }

    @PutMapping("/{key}")
    public CacheMutationResponse put(
        @PathVariable @Size(max = 128) String key,
        @Valid @RequestBody CacheValueRequest request
    ) {
        return caches.put(key, request.value());
    }

    @DeleteMapping("/{key}")
    public CacheMutationResponse evict(@PathVariable @Size(max = 128) String key) {
        return caches.evict(key);
    }

    @DeleteMapping("/{tier}/{key}")
    public CacheMutationResponse evictTier(
        @PathVariable @Pattern(regexp = "(?i)L[123]") String tier,
        @PathVariable @Size(max = 128) String key
    ) {
        return caches.evictTier(tier, key);
    }

    @DeleteMapping
    public CacheMutationResponse clear() {
        return caches.clear();
    }
}
