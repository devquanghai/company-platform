package com.company.platform.cache.autoconfigure;

import com.company.platform.cache.api.operation.PlatformCacheOperations;
import com.company.platform.cache.api.operation.TypedCacheFactory;
import com.company.platform.cache.autoconfigure.properties.PlatformCacheProperties;
import com.company.platform.cache.internal.application.service.DefaultTypedCacheFactory;
import com.company.platform.cache.internal.springcache.SpringCachePlatformCacheOperations;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.cache.autoconfigure.CacheAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(after = CacheAutoConfiguration.class)
@ConditionalOnClass(CacheManager.class)
@org.springframework.boot.autoconfigure.condition.ConditionalOnSingleCandidate(
    CacheManager.class)
@ConditionalOnProperty(prefix = "platform.cache", name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties(PlatformCacheProperties.class)
public class PlatformCacheAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(PlatformCacheOperations.class)
    PlatformCacheOperations platformCacheOperations(
        CacheManager cacheManager,
        PlatformCacheProperties properties
    ) {
        return new SpringCachePlatformCacheOperations(cacheManager, properties);
    }

    @Bean
    @ConditionalOnMissingBean(TypedCacheFactory.class)
    TypedCacheFactory platformTypedCacheFactory(PlatformCacheOperations operations) {
        return new DefaultTypedCacheFactory(operations);
    }
}
