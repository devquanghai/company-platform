package com.company.platform.cache.autoconfigure;

import com.company.platform.cache.application.port.out.CacheBackendRegistry;
import com.company.platform.cache.observability.event.CacheEventPublisher;
import com.company.platform.cache.observability.event.SpringCacheEventPublisher;
import com.company.platform.cache.observability.health.PlatformCacheHealthIndicator;
import com.company.platform.cache.observability.metrics.CacheMetricsRecorder;
import com.company.platform.cache.observability.metrics.MicrometerCacheMetricsRecorder;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(
    after = PlatformCacheAutoConfiguration.class,
    afterName =
        "org.springframework.boot.micrometer.metrics.autoconfigure.export.simple."
            + "SimpleMetricsExportAutoConfiguration")
@ConditionalOnProperty(
    prefix = "platform.cache", name = "enabled", matchIfMissing = true)
public class CacheObservabilityAutoConfiguration {

    @Bean
    @ConditionalOnClass(MeterRegistry.class)
    @ConditionalOnBean(MeterRegistry.class)
    @ConditionalOnMissingBean
    @ConditionalOnProperty(
        prefix = "platform.cache.observability", name = "metrics-enabled",
        havingValue = "true", matchIfMissing = true)
    CacheMetricsRecorder platformCacheMetricsRecorder(MeterRegistry registry) {
        return new MicrometerCacheMetricsRecorder(registry);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(
        prefix = "platform.cache.observability", name = "events-enabled",
        havingValue = "true", matchIfMissing = true)
    CacheEventPublisher platformCacheEventPublisher(
        ApplicationEventPublisher publisher
    ) {
        return new SpringCacheEventPublisher(publisher);
    }

    @Bean("platformCacheHealthIndicator")
    @ConditionalOnClass(HealthIndicator.class)
    @ConditionalOnMissingBean(name = "platformCacheHealthIndicator")
    @ConditionalOnProperty(
        prefix = "platform.cache.observability", name = "health-enabled",
        havingValue = "true", matchIfMissing = true)
    HealthIndicator platformCacheHealthIndicator(
        CacheBackendRegistry backends
    ) {
        return new PlatformCacheHealthIndicator(backends);
    }
}
