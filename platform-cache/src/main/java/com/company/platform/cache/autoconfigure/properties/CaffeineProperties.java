package com.company.platform.cache.autoconfigure.properties;

import jakarta.validation.constraints.Min;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.time.Duration;

@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CaffeineProperties {
    @Min(1) long maximumSize = 10_000L;
    Duration expireAfterWrite = Duration.ofMinutes(10);
    Duration expireAfterAccess;
    Duration refreshAfterWrite;
    boolean recordStats = true;
    boolean weakKeys;
    boolean weakValues;
    boolean softValues;
}
