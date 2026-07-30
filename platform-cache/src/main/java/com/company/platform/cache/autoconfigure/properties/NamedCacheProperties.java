package com.company.platform.cache.autoconfigure.properties;

import com.company.platform.cache.domain.policy.CacheFailurePolicy;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.time.Duration;

@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class NamedCacheProperties {
    boolean enabled = true;
    String store;
    Duration ttl;
    boolean cacheNullValues;
    CacheFailurePolicy failurePolicy = CacheFailurePolicy.FAIL_OPEN;
    boolean coordination;
    @Valid CacheKeyProperties key = new CacheKeyProperties();
    @Valid FallbackProperties fallback = new FallbackProperties();
    @Valid MultiLevelProperties multiLevel = new MultiLevelProperties();
    @Valid StampedeProperties stampede = new StampedeProperties();
    @Valid TtlJitterProperties ttlJitter = new TtlJitterProperties();
    @Valid NegativeCacheProperties negativeCache = new NegativeCacheProperties();
}
