package com.company.platform.cache.adapter.springcache;

import com.company.platform.cache.adapter.caffeine.CaffeineCacheBackend;
import com.company.platform.cache.adapter.caffeine.CaffeineCacheSettings;
import com.company.platform.cache.application.resolver.CacheDefinitionRegistry;
import com.company.platform.cache.application.resolver.DefaultCacheBackendRegistry;
import com.company.platform.cache.application.service.DefaultPlatformCacheOperations;
import com.company.platform.cache.consistency.SingleFlightCoordinator;
import com.company.platform.cache.support.CacheTestFixtures;
import com.company.platform.cache.support.DefaultCacheKeyEncoder;
import com.company.platform.cache.support.PlatformCachePropertiesValidator;
import com.company.platform.core.json.JsonMapperHelper;
import com.company.platform.core.time.TimeProvider;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlatformSpringCacheManagerTest {

    @Test
    void exposesConfiguredCacheAndSupportsSpringContract() {
        var operations = operations();
        PlatformSpringCacheManager manager =
            new PlatformSpringCacheManager(operations, definitions());
        Cache cache = manager.getCache("users");

        assertThat(manager.getCacheNames()).containsExactly("users");
        assertThat(manager.getCache("missing")).isNull();
        assertThat(cache.getName()).isEqualTo("users");
        assertThat(cache.getNativeCache()).isSameAs(operations);
        assertThat(cache.get("missing")).isNull();

        cache.put("key", "value");
        assertThat(cache.get("key").get()).isEqualTo("value");
        assertThat(cache.get("key", String.class)).isEqualTo("value");
        assertThat(cache.putIfAbsent("key", "ignored").get()).isEqualTo("value");
        assertThat(cache.putIfAbsent("other", "new")).isNull();
        assertThat(cache.evictIfPresent("other")).isTrue();
        cache.evict("key");
        assertThat(cache.get("key")).isNull();
    }

    @Test
    void supportsLoaderClearInvalidateAndLoaderFailure() {
        Cache cache = new PlatformSpringCacheManager(operations(), definitions())
            .getCache("users");
        AtomicInteger loads = new AtomicInteger();
        assertThat(cache.get("key", () -> {
            loads.incrementAndGet();
            return "loaded";
        })).isEqualTo("loaded");
        assertThat(cache.get("key", () -> "wrong")).isEqualTo("loaded");
        assertThat(loads).hasValue(1);
        cache.clear();
        assertThat(cache.invalidate()).isTrue();
        assertThatThrownBy(() -> cache.get("failure", () -> {
            throw new java.io.IOException("loader");
        })).hasCauseInstanceOf(java.io.IOException.class);
    }

    @Test
    void validatesConstructor() {
        assertThatNullPointerException().isThrownBy(() ->
            new PlatformSpringCacheManager(null, definitions()));
        assertThatNullPointerException().isThrownBy(() ->
            new PlatformSpringCacheManager(operations(), null));
    }

    private DefaultPlatformCacheOperations operations() {
        JsonMapperHelper json = new JsonMapperHelper(JsonMapper.builder().build());
        return new DefaultPlatformCacheOperations(
            definitions(),
            new DefaultCacheBackendRegistry(Map.of(
                "users",
                new CaffeineCacheBackend(CaffeineCacheSettings.builder()
                    .maximumSize(100)
                    .defaultTtl(Duration.ofMinutes(10))
                    .build()))),
            new DefaultCacheKeyEncoder(json),
            json,
            new SingleFlightCoordinator(),
            event -> { },
            new FixedTimeProvider(),
            event -> { });
    }

    private CacheDefinitionRegistry definitions() {
        var properties = CacheTestFixtures.validProperties();
        return new CacheDefinitionRegistry(
            properties, new PlatformCachePropertiesValidator(properties));
    }

    private static final class FixedTimeProvider implements TimeProvider {
        @Override public Instant nowInstant() { return Instant.EPOCH; }
        @Override public OffsetDateTime now() { return Instant.EPOCH.atOffset(ZoneOffset.UTC); }
        @Override public OffsetDateTime now(ZoneId zoneId) {
            return Instant.EPOCH.atZone(zoneId).toOffsetDateTime();
        }
        @Override public ZoneId getDefaultZone() { return ZoneOffset.UTC; }
    }
}
