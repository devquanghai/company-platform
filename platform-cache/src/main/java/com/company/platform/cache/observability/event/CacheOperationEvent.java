package com.company.platform.cache.observability.event;

import com.company.platform.cache.domain.model.CacheProviderType;
import com.company.platform.cache.domain.model.CacheResultStatus;
import com.company.platform.cache.domain.model.CacheTier;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

import java.time.Duration;
import java.time.OffsetDateTime;

@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public final class CacheOperationEvent {
    OffsetDateTime timestamp;
    String cacheName;
    String operation;
    CacheProviderType provider;
    CacheResultStatus outcome;
    CacheTier tier;
    boolean fallback;
    boolean stale;
    Duration duration;
    String errorCategory;
}
