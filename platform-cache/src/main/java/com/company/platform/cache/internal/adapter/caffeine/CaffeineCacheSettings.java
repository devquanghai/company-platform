package com.company.platform.cache.internal.adapter.caffeine;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

import java.time.Duration;

/**
 * Immutable provider-specific settings used to construct one bounded Caffeine cache.
 */
@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public final class CaffeineCacheSettings {
    String spec;
    Duration defaultTtl;
}
