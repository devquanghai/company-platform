package com.company.platform.cache.domain.result;

import com.company.platform.cache.domain.model.CacheFailure;
import com.company.platform.cache.domain.model.CacheProviderType;
import com.company.platform.cache.domain.model.CacheResultStatus;
import com.company.platform.cache.domain.model.CacheTier;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

import java.time.Duration;

@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public final class CacheResult<T> {
    CacheResultStatus status;
    T value;
    String cacheName;
    CacheProviderType provider;
    CacheTier tier;
    boolean stale;
    Duration latency;
    CacheFailure failure;
}
