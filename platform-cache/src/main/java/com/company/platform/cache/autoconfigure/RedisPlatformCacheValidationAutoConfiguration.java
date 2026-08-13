package com.company.platform.cache.autoconfigure;

import com.company.platform.cache.api.operation.PlatformCacheOperations;
import com.company.platform.cache.domain.exception.PlatformCacheConfigurationException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;

@AutoConfiguration(after = PlatformCacheAutoConfiguration.class)
@ConditionalOnClass(RedisConnectionFactory.class)
@ConditionalOnProperty(prefix = "platform.cache", name = "enabled", matchIfMissing = true)
@ConditionalOnProperty(prefix = "platform.cache", name = "provider", havingValue = "redis")
public class RedisPlatformCacheValidationAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(PlatformCacheOperations.class)
    Object platformRedisCacheInfrastructureValidator(
        ObjectProvider<RedisConnectionFactory> connectionFactories,
        ObjectProvider<CacheManager> cacheManagers
    ) {
        if (connectionFactories.getIfUnique() == null || cacheManagers.getIfUnique() == null) {
            throw new PlatformCacheConfigurationException(
                "platform.cache.provider=REDIS requires one Boot-managed "
                    + "RedisConnectionFactory and CacheManager");
        }
        throw new PlatformCacheConfigurationException(
            "platform.cache.provider=REDIS did not create PlatformCacheOperations");
    }
}
