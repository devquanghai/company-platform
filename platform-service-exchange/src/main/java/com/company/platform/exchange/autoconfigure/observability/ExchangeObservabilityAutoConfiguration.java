package com.company.platform.exchange.autoconfigure.observability;

import com.company.platform.exchange.autoconfigure.PlatformServiceExchangeAutoConfiguration;
import com.company.platform.exchange.observability.metrics.ExchangeMetrics;
import com.company.platform.exchange.observability.metrics.MicrometerExchangeMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(after = PlatformServiceExchangeAutoConfiguration.class)
@ConditionalOnClass(MeterRegistry.class)
@ConditionalOnProperty(
    prefix = "platform.service-exchange", name = "enabled",
    havingValue = "true", matchIfMissing = true)
public class ExchangeObservabilityAutoConfiguration {

    @Bean
    @ConditionalOnBean(MeterRegistry.class)
    @ConditionalOnMissingBean
    public ExchangeMetrics exchangeMetrics(MeterRegistry registry) {
        return new MicrometerExchangeMetrics(registry);
    }
}
