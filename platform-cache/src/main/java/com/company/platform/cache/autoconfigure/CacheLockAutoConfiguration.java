package com.company.platform.cache.autoconfigure;

import com.company.platform.cache.api.lock.DistributedLockOperations;
import com.company.platform.cache.domain.exception.PlatformCacheConfigurationException;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(after = PlatformCacheAutoConfiguration.class)
@ConditionalOnProperty(
    prefix = "platform.cache.locking", name = "enabled", havingValue = "true")
public class CacheLockAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    DistributedLockOperations platformDistributedLockOperations() {
        throw new PlatformCacheConfigurationException(
            "platform.cache.locking.enabled requires an application-provided "
                + "DistributedLockOperations adapter");
    }
}
