package com.company.platform.cache.autoconfigure;

import com.company.platform.cache.api.operation.AtomicCacheOperations;
import com.company.platform.cache.api.operation.OptimisticCacheOperations;
import com.company.platform.cache.api.operation.PlatformCacheOperations;
import com.company.platform.cache.api.operation.TypedCacheFactory;
import com.company.platform.cache.application.port.out.CacheBackendRegistry;
import com.company.platform.cache.application.resolver.CacheDefinitionRegistry;
import com.company.platform.cache.observability.event.CacheEventPublisher;
import com.company.platform.cache.observability.metrics.CacheMetricsRecorder;
import com.company.platform.core.json.JsonMapperHelper;
import com.company.platform.core.time.TimeProvider;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformCacheAutoConfigurationTest {
    private final ApplicationContextRunner contextRunner =
        new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                PlatformCacheAutoConfiguration.class,
                SpringCacheBridgeAutoConfiguration.class,
                CacheObservabilityAutoConfiguration.class,
                CacheLockAutoConfiguration.class))
            .withUserConfiguration(CoreTestConfiguration.class)
            .withPropertyValues(
                "platform.cache.stores.local.provider=CAFFEINE",
                "platform.cache.stores.local.caffeine.maximum-size=100",
                "platform.cache.caches.users.store=local");

    @Test
    void createsCaffeineFacadeTypedAtomicAndSpringBridge() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(PlatformCacheOperations.class);
            assertThat(context).hasSingleBean(TypedCacheFactory.class);
            assertThat(context).hasSingleBean(AtomicCacheOperations.class);
            assertThat(context).hasSingleBean(OptimisticCacheOperations.class);
            assertThat(context).hasSingleBean(CacheBackendRegistry.class);
            assertThat(context).hasSingleBean(CacheDefinitionRegistry.class);
            assertThat(context).hasSingleBean(CacheManager.class);
            assertThat(context).hasSingleBean(CacheMetricsRecorder.class);
            assertThat(context).hasSingleBean(CacheEventPublisher.class);
            assertThat(context.getBean(CacheManager.class).getCacheNames())
                .containsExactly("users");
            PlatformCacheOperations operations =
                context.getBean(PlatformCacheOperations.class);
            operations.put("users", "user-1", "active");
            assertThat(operations.get("users", "user-1", String.class))
                .contains("active");
            assertThat(context.getBean(MeterRegistry.class)
                .find("platform.cache.operations").counters()).isNotEmpty();
        });
    }

    @Test
    void disabledPlatformBacksOffCompletely() {
        contextRunner.withPropertyValues("platform.cache.enabled=false")
            .run(context -> {
                assertThat(context).doesNotHaveBean(PlatformCacheOperations.class);
                assertThat(context).doesNotHaveBean(CacheBackendRegistry.class);
                assertThat(context).doesNotHaveBean(CacheManager.class);
            });
    }

    @Test
    void annotationsCanBeDisabledWithoutDisablingFacade() {
        contextRunner.withPropertyValues("platform.cache.annotations-enabled=false")
            .run(context -> {
                assertThat(context).hasSingleBean(PlatformCacheOperations.class);
                assertThat(context).doesNotHaveBean(CacheManager.class);
            });
    }

    @Test
    void invalidStoreReferenceFailsStartup() {
        new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                PlatformCacheAutoConfiguration.class))
            .withUserConfiguration(CoreTestConfiguration.class)
            .withPropertyValues(
                "platform.cache.stores.local.provider=CAFFEINE",
                "platform.cache.caches.users.store=missing")
            .run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure())
                    .hasRootCauseMessage(
                        "platform.cache.caches.users references unknown store missing");
            });
    }

    @Configuration(proxyBeanMethods = false)
    static class CoreTestConfiguration {
        @Bean
        JsonMapperHelper jsonMapperHelper() {
            return new JsonMapperHelper(JsonMapper.builder().build());
        }

        @Bean
        TimeProvider timeProvider() {
            return new FixedTimeProvider();
        }

        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
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
