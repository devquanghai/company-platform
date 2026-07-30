package com.company.platform.cache.autoconfigure;

import com.company.platform.cache.adapter.springcache.PlatformSpringCacheManager;
import com.company.platform.cache.api.operation.PlatformCacheOperations;
import com.company.platform.cache.application.resolver.CacheDefinitionRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.cache.autoconfigure.CacheAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(
    after = PlatformCacheAutoConfiguration.class,
    before = CacheAutoConfiguration.class)
@ConditionalOnClass(CacheManager.class)
@ConditionalOnBean(PlatformCacheOperations.class)
@ConditionalOnProperty(
    prefix = "platform.cache", name = "annotations-enabled",
    havingValue = "true", matchIfMissing = true)
@EnableCaching
public class SpringCacheBridgeAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(CacheManager.class)
    CacheManager platformCacheManager(
        PlatformCacheOperations operations,
        CacheDefinitionRegistry definitions
    ) {
        return new PlatformSpringCacheManager(operations, definitions);
    }
}
