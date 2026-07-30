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
public class RedisPoolProperties {
    boolean enabled = true;
    @Min(1) int maxActive = 50;
    @Min(0) int maxIdle = 20;
    @Min(0) int minIdle = 5;
    Duration maxWait = Duration.ofSeconds(1);
    Duration timeBetweenEvictionRuns = Duration.ofSeconds(30);
}
