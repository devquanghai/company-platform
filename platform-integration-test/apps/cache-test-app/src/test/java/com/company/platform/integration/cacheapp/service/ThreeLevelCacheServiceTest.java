package com.company.platform.integration.cacheapp.service;

import com.company.platform.cache.api.operation.PlatformCacheOperations;
import com.company.platform.cache.domain.model.CacheResultStatus;
import com.company.platform.cache.domain.model.CacheTier;
import com.company.platform.cache.domain.result.CacheResult;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class ThreeLevelCacheServiceTest {
    private final PlatformCacheOperations cache = mock(PlatformCacheOperations.class);
    private final ThreeLevelCacheService service = new ThreeLevelCacheService(cache);

    @Test
    void readsL3AndBackfillsBothCaffeineTiers() {
        when(cache.getResult(ThreeLevelCacheService.L1_CACHE, "key", String.class))
            .thenReturn(result(CacheResultStatus.MISS, null));
        when(cache.getResult(ThreeLevelCacheService.L2_CACHE, "key", String.class))
            .thenReturn(result(CacheResultStatus.MISS, null));
        when(cache.getResult(ThreeLevelCacheService.L3_CACHE, "key", String.class))
            .thenReturn(result(CacheResultStatus.HIT, "value"));

        var response = service.get("key");

        assertThat(response.hit()).isTrue();
        assertThat(response.source()).isEqualTo("L3_REDIS");
        assertThat(response.promotedTo())
            .containsExactly("L2_CAFFEINE", "L1_CAFFEINE");
        InOrder order = inOrder(cache);
        order.verify(cache).put(ThreeLevelCacheService.L2_CACHE, "key", "value");
        order.verify(cache).put(ThreeLevelCacheService.L1_CACHE, "key", "value");
    }

    @Test
    void readsL1WithoutCallingLowerTiers() {
        when(cache.getResult(ThreeLevelCacheService.L1_CACHE, "key", String.class))
            .thenReturn(result(CacheResultStatus.HIT, "value"));

        var response = service.get("key");

        assertThat(response.source()).isEqualTo("L1_CAFFEINE");
        assertThat(response.promotedTo()).isEmpty();
        verify(cache).getResult(ThreeLevelCacheService.L1_CACHE, "key", String.class);
        verifyNoMoreInteractions(cache);
    }

    @Test
    void writesRemoteTierBeforeLocalTiers() {
        service.put("key", "value");

        InOrder order = inOrder(cache);
        order.verify(cache).put(ThreeLevelCacheService.L3_CACHE, "key", "value");
        order.verify(cache).put(ThreeLevelCacheService.L2_CACHE, "key", "value");
        order.verify(cache).put(ThreeLevelCacheService.L1_CACHE, "key", "value");
    }

    private CacheResult<String> result(CacheResultStatus status, String value) {
        return CacheResult.<String>builder()
            .status(status)
            .value(value)
            .tier(CacheTier.NONE)
            .build();
    }
}
