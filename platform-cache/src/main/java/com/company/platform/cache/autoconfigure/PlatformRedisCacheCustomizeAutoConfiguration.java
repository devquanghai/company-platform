package com.company.platform.cache.autoconfigure;

import java.time.Duration;

import com.company.platform.cache.autoconfigure.properties.PlatformRedisCustomizeProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.cache.autoconfigure.RedisCacheManagerBuilderCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;

@AutoConfiguration
@ConditionalOnClass({
    RedisCacheManager.class,
    RedisCacheConfiguration.class
})
@ConditionalOnProperty(
    prefix = "platform.cache",
    name = "provider",
    havingValue = "redis"
)
@EnableConfigurationProperties(
    PlatformRedisCustomizeProperties.class
)
public class PlatformRedisCacheCustomizeAutoConfiguration {

    @Bean
    RedisCacheManagerBuilderCustomizer
    platformRedisCacheManagerBuilderCustomizer(
        PlatformRedisCustomizeProperties properties
    ) {

        return builder -> {

            RedisCacheConfiguration defaultConfiguration =
                builder.cacheDefaults();

            properties.getCustomize()
                .forEach((cacheName, custom) -> {

                    validate(
                        cacheName,
                        custom
                    );

                    /*
                     * Nếu cache đã được custom bởi customizer khác,
                     * kế thừa config đó.
                     *
                     * Nếu chưa có thì kế thừa default config
                     * mà Spring Boot đã setup.
                     */
                    RedisCacheConfiguration configuration =
                        builder
                            .getCacheConfigurationFor(
                                cacheName
                            )
                            .orElse(
                                defaultConfiguration
                            );

                    if (custom.getTtl() != null) {

                        configuration =
                            configuration.entryTtl(
                                custom.getTtl()
                            );
                    }

                    builder.withCacheConfiguration(
                        cacheName,
                        configuration
                    );
                });
        };
    }

    private void validate(
        String cacheName,
        PlatformRedisCustomizeProperties.CacheConfiguration configuration
    ) {

        if (cacheName == null || cacheName.isBlank()) {
            throw new IllegalArgumentException(
                "Redis cache name must not be blank"
            );
        }

        if (configuration == null) {
            throw new IllegalArgumentException(
                "Redis cache configuration must not be null: "
                    + cacheName
            );
        }

        Duration ttl = configuration.getTtl();

        if (ttl != null && ttl.isNegative()) {
            throw new IllegalArgumentException(
                "Redis cache TTL must not be negative: "
                    + cacheName
            );
        }
    }
}
