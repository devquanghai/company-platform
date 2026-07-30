package com.company.platform.cache.application.port.out;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

import java.time.Duration;
import com.company.platform.cache.domain.model.CacheTier;

/**
 * Provider-neutral value returned by a cache backend.
 */
@Getter
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public final class BackendCacheEntry {
    Object value;
    long version;
    Duration remainingTtl;
    boolean stale;
    CacheTier tier;

    public BackendCacheEntry(Object value, long version, Duration remainingTtl) {
        this(value, version, remainingTtl, false, CacheTier.NONE);
    }

    public BackendCacheEntry(
        Object value,
        long version,
        Duration remainingTtl,
        boolean stale,
        CacheTier tier
    ) {
        this.value = value;
        this.version = version;
        this.remainingTtl = remainingTtl;
        this.stale = stale;
        this.tier = tier == null ? CacheTier.NONE : tier;
    }
}
