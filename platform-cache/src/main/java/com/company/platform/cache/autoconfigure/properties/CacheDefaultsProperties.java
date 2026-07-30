package com.company.platform.cache.autoconfigure.properties;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.time.Duration;

@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CacheDefaultsProperties {
    Duration ttl = Duration.ofMinutes(10);
    String keyPrefix = "application:cache";
    boolean cacheNullValues;
    boolean keyHashEnabled;
    long maximumEntrySize = 1_048_576L;
}
