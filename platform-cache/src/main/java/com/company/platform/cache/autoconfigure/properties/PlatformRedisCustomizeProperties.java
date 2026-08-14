package com.company.platform.cache.autoconfigure.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.convert.DurationUnit;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "spring.cache.redis")
@Getter
@Setter
public class PlatformRedisCustomizeProperties {

    private Map<String, CacheConfiguration> customize =
        new LinkedHashMap<>();

    @Getter
    @Setter
    public static class CacheConfiguration {

        /**
         * Numeric value defaults to seconds.
         * <p>
         * Examples:
         * <p>
         * ttl: 3600
         * ttl: 1h
         * ttl: 60m
         */
        @DurationUnit(ChronoUnit.SECONDS)
        private Duration ttl;
    }
}
