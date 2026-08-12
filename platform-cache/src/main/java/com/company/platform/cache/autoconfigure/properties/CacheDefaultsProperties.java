package com.company.platform.cache.autoconfigure.properties;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.time.Duration;
import org.springframework.util.unit.DataSize;

@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CacheDefaultsProperties {
    Duration ttl = Duration.ofMinutes(10);
    String keyPrefix = "application:cache";
    boolean cacheNullValues;
    DataSize maximumEntrySize = DataSize.ofMegabytes(1);
}
