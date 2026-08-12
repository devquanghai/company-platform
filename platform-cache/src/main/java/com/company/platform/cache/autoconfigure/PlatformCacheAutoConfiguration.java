package com.company.platform.cache.autoconfigure;

import com.company.platform.cache.internal.adapter.caffeine.CaffeineCacheBackend;
import com.company.platform.cache.internal.adapter.caffeine.CaffeineCacheSettings;
import com.company.platform.cache.internal.adapter.fallback.FallbackCacheBackend;
import com.company.platform.cache.internal.adapter.multilevel.MultiLevelCacheBackend;
import com.company.platform.cache.internal.adapter.noop.NoOpCacheBackend;
import com.company.platform.cache.api.operation.AtomicCacheOperations;
import com.company.platform.cache.api.operation.OptimisticCacheOperations;
import com.company.platform.cache.api.operation.PlatformCacheOperations;
import com.company.platform.cache.api.operation.TypedCacheFactory;
import com.company.platform.cache.internal.application.port.out.CacheBackend;
import com.company.platform.cache.internal.application.port.out.CacheBackendRegistry;
import com.company.platform.cache.internal.application.port.out.CacheKeyEncoder;
import com.company.platform.cache.internal.application.port.out.RedisCacheBackendFactory;
import com.company.platform.cache.internal.application.resolver.CacheDefinitionRegistry;
import com.company.platform.cache.internal.application.resolver.DefaultCacheBackendRegistry;
import com.company.platform.cache.internal.application.resolver.NamedCacheDefinition;
import com.company.platform.cache.internal.application.service.DefaultPlatformCacheOperations;
import com.company.platform.cache.internal.application.service.DefaultTypedCacheFactory;
import com.company.platform.cache.autoconfigure.properties.CacheStoreProperties;
import com.company.platform.cache.autoconfigure.properties.PlatformCacheProperties;
import com.company.platform.cache.domain.model.CacheProviderType;
import com.company.platform.cache.internal.support.DefaultCacheKeyEncoder;
import com.company.platform.cache.internal.support.PlatformCachePropertiesValidator;
import com.company.platform.cache.internal.consistency.SingleFlightCoordinator;
import com.company.platform.cache.observability.event.CacheEventPublisher;
import com.company.platform.core.time.TimeProvider;
import com.company.platform.cache.observability.metrics.CacheMetricsRecorder;
import com.company.platform.core.json.JsonMapperHelper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.cache.autoconfigure.CacheProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@AutoConfiguration(after = RedisCacheAutoConfiguration.class)
@ConditionalOnProperty(
    prefix = "platform.cache", name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties({PlatformCacheProperties.class, CacheProperties.class})
public class PlatformCacheAutoConfiguration {

    private final CacheProperties cacheProperties;

    public PlatformCacheAutoConfiguration(CacheProperties cacheProperties) {
        this.cacheProperties = cacheProperties;
    }

    @Bean
    @ConditionalOnMissingBean
    PlatformCachePropertiesValidator platformCachePropertiesValidator(
        PlatformCacheProperties properties
    ) {
        return new PlatformCachePropertiesValidator(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    CacheDefinitionRegistry platformCacheDefinitionRegistry(
        PlatformCacheProperties properties,
        PlatformCachePropertiesValidator validator
    ) {
        return new CacheDefinitionRegistry(properties, validator);
    }

    @Bean
    @ConditionalOnMissingBean
    CacheKeyEncoder platformCacheKeyEncoder(JsonMapperHelper json) {
        return new DefaultCacheKeyEncoder(json);
    }

    @Bean
    @ConditionalOnMissingBean
    CacheBackendRegistry platformCacheBackendRegistry(
        CacheDefinitionRegistry definitions,
        ObjectProvider<RedisCacheBackendFactory> redis
    ) {
        Map<String, CacheBackend> backends = new LinkedHashMap<>();
        definitions.getCaches().values().forEach(cache ->
            backends.put(cache.getName(), buildBackend(
                definitions, cache, redis.getIfAvailable())));
        return new DefaultCacheBackendRegistry(backends);
    }

    @Bean
    @ConditionalOnMissingBean
    SingleFlightCoordinator platformCacheSingleFlightCoordinator() {
        return new SingleFlightCoordinator();
    }

    @Bean
    @ConditionalOnMissingBean(PlatformCacheOperations.class)
    DefaultPlatformCacheOperations platformCacheOperations(
        CacheDefinitionRegistry definitions,
        CacheBackendRegistry backends,
        CacheKeyEncoder keys,
        JsonMapperHelper json,
        SingleFlightCoordinator singleFlight,
        ObjectProvider<CacheEventPublisher> events,
        TimeProvider time,
        ObjectProvider<CacheMetricsRecorder> metrics
    ) {
        return new DefaultPlatformCacheOperations(
            definitions, backends, keys, json, singleFlight,
            events.getIfAvailable(() -> ignored -> { }), time,
            metrics.getIfAvailable(() -> ignored -> { }));
    }

    @Bean
    @ConditionalOnMissingBean(TypedCacheFactory.class)
    TypedCacheFactory platformTypedCacheFactory(PlatformCacheOperations operations) {
        return new DefaultTypedCacheFactory(operations);
    }

    @Bean
    @ConditionalOnBean(DefaultPlatformCacheOperations.class)
    @ConditionalOnMissingBean(AtomicCacheOperations.class)
    AtomicCacheOperations platformAtomicCacheOperations(
        DefaultPlatformCacheOperations operations
    ) {
        return operations;
    }

    @Bean
    @ConditionalOnBean(DefaultPlatformCacheOperations.class)
    @ConditionalOnMissingBean(OptimisticCacheOperations.class)
    OptimisticCacheOperations platformOptimisticCacheOperations(
        DefaultPlatformCacheOperations operations
    ) {
        return operations;
    }

    private CacheBackend buildBackend(
        CacheDefinitionRegistry definitions,
        NamedCacheDefinition cache,
        RedisCacheBackendFactory redis
    ) {
        CacheBackend primary;
        if (cache.isMultiLevel()) {
            CacheBackend l1 = caffeine(cache.getL1Store().getProperties(), cache,
                cache.getProperties().getMultiLevel().getL1Ttl());
            CacheBackend l2 = redis(redis).create(
                cache.getL2Store().getName(), cache.getName());
            primary = new MultiLevelCacheBackend(
                l1, l2, cache.getProperties().getMultiLevel().getL1Ttl(),
                cache.getProperties().getMultiLevel().isPopulateL1OnL2Hit());
        } else {
            primary = switch (cache.getProvider()) {
                case CAFFEINE -> caffeine(
                    cache.getPrimaryStore().getProperties(), cache, cache.getTtl());
                case REDIS -> redis(redis).create(
                    cache.getPrimaryStore().getName(), cache.getName());
                case NOOP -> new NoOpCacheBackend();
                case MULTI_LEVEL -> throw new IllegalStateException(
                    "MULTI_LEVEL requires explicit L1 and L2 stores");
            };
        }
        if (!cache.getProperties().getFallback().isEnabled()) {
            return primary;
        }
        CacheStoreProperties fallbackStore = definitions.requireStore(
            cache.getProperties().getFallback().getLocalStore()).getProperties();
        CacheBackend local = caffeine(fallbackStore, cache,
            cache.getProperties().getFallback().getTtl());
        return new FallbackCacheBackend(
            primary, local, cache.getProperties().getFallback().getMode(),
            cache.getProperties().getFallback().getTtl(),
            cache.getProperties().getFallback().getMaximumStale(),
            cache.getProperties().getFallback().isAllowLocalWriteFallback(),
            cache.getProperties().getFallback().isClearOnPrimaryRecovery());
    }

    private CacheBackend caffeine(
        CacheStoreProperties store,
        NamedCacheDefinition cache,
        Duration ttl
    ) {
        if (store == null || store.getProvider() != CacheProviderType.CAFFEINE) {
            throw new IllegalStateException("Caffeine store is unavailable");
        }
        return new CaffeineCacheBackend(CaffeineCacheSettings.builder()
            .spec(cacheProperties.getCaffeine().getSpec())
            .defaultTtl(ttl)
            .build());
    }

    private RedisCacheBackendFactory redis(
        RedisCacheBackendFactory factory
    ) {
        if (factory == null) {
            throw new IllegalStateException(
                "Redis cache configured but Spring Data Redis is not available");
        }
        return factory;
    }
}
