package com.company.platform.cache.autoconfigure;

import com.company.platform.cache.api.observability.CacheOperationObservability;
import com.company.platform.cache.internal.observability.MicrometerCacheOperationObservability;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(afterName =
    "org.springframework.boot.micrometer.observation.autoconfigure.ObservationAutoConfiguration")
@ConditionalOnClass(ObservationRegistry.class)
@ConditionalOnProperty(prefix = "platform.cache", name = "enabled", matchIfMissing = true)
public class CacheObservabilityAutoConfiguration {

    @Bean
    @ConditionalOnBean(ObservationRegistry.class)
    @ConditionalOnMissingBean(CacheOperationObservability.class)
    CacheOperationObservability platformCacheOperationObservability(
        ObservationRegistry registry
    ) {
        return new MicrometerCacheOperationObservability(registry);
    }
}
