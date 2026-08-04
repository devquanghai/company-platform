package com.company.platform.integration.cacheapp.service;

import com.company.platform.cache.api.operation.PlatformCacheOperations;
import com.company.platform.cache.domain.model.CacheResultStatus;
import com.company.platform.cache.domain.result.CacheResult;
import com.company.platform.integration.cacheapp.api.CacheMutationResponse;
import com.company.platform.integration.cacheapp.api.ThreeLevelCacheReadResponse;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

@Service
public class ThreeLevelCacheService {
    public static final String L1_CACHE = "demo-three-level-l1";
    public static final String L2_CACHE = "demo-three-level-l2";
    public static final String L3_CACHE = "demo-three-level-l3";

    private final PlatformCacheOperations cache;

    public ThreeLevelCacheService(PlatformCacheOperations cache) {
        this.cache = cache;
    }

    public synchronized ThreeLevelCacheReadResponse get(String key) {
        CacheResult<String> l1 = cache.getResult(L1_CACHE, key, String.class);
        if (isHit(l1)) {
            return hit(key, l1.getValue(), "L1_CAFFEINE", List.of());
        }

        CacheResult<String> l2 = cache.getResult(L2_CACHE, key, String.class);
        if (isHit(l2)) {
            cache.put(L1_CACHE, key, l2.getValue());
            return hit(key, l2.getValue(), "L2_CAFFEINE", List.of("L1_CAFFEINE"));
        }

        CacheResult<String> l3 = cache.getResult(L3_CACHE, key, String.class);
        if (isHit(l3)) {
            cache.put(L2_CACHE, key, l3.getValue());
            cache.put(L1_CACHE, key, l3.getValue());
            return hit(
                key,
                l3.getValue(),
                "L3_REDIS",
                List.of("L2_CAFFEINE", "L1_CAFFEINE"));
        }
        return new ThreeLevelCacheReadResponse(key, false, null, "MISS", List.of());
    }

    public synchronized CacheMutationResponse put(String key, String value) {
        cache.put(L3_CACHE, key, value);
        cache.put(L2_CACHE, key, value);
        cache.put(L1_CACHE, key, value);
        return new CacheMutationResponse(
            key,
            "WRITE_THROUGH",
            List.of("L3_REDIS", "L2_CAFFEINE", "L1_CAFFEINE"));
    }

    public synchronized CacheMutationResponse evict(String key) {
        cache.evict(L1_CACHE, key);
        cache.evict(L2_CACHE, key);
        cache.evict(L3_CACHE, key);
        return new CacheMutationResponse(
            key,
            "EVICT_ALL",
            List.of("L1_CAFFEINE", "L2_CAFFEINE", "L3_REDIS"));
    }

    public synchronized CacheMutationResponse evictTier(String rawTier, String key) {
        Tier tier = Tier.valueOf(rawTier.toUpperCase(Locale.ROOT));
        cache.evict(tier.cacheName, key);
        return new CacheMutationResponse(key, "EVICT", List.of(tier.label));
    }

    public synchronized CacheMutationResponse clear() {
        cache.clear(L1_CACHE);
        cache.clear(L2_CACHE);
        cache.clear(L3_CACHE);
        return new CacheMutationResponse(
            "*",
            "CLEAR_ALL",
            List.of("L1_CAFFEINE", "L2_CAFFEINE", "L3_REDIS"));
    }

    private boolean isHit(CacheResult<String> result) {
        return result.getStatus() == CacheResultStatus.HIT
            || result.getStatus() == CacheResultStatus.HIT_FALLBACK;
    }

    private ThreeLevelCacheReadResponse hit(
        String key, String value, String source, List<String> promotedTo
    ) {
        return new ThreeLevelCacheReadResponse(key, true, value, source, promotedTo);
    }

    private enum Tier {
        L1(L1_CACHE, "L1_CAFFEINE"),
        L2(L2_CACHE, "L2_CAFFEINE"),
        L3(L3_CACHE, "L3_REDIS");

        private final String cacheName;
        private final String label;

        Tier(String cacheName, String label) {
            this.cacheName = cacheName;
            this.label = label;
        }
    }
}
