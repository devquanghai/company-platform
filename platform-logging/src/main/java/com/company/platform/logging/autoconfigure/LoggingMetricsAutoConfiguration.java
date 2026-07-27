package com.company.platform.logging.autoconfigure;

import com.company.platform.logging.observability.metrics.LoggingMetrics;
import com.company.platform.logging.observability.metrics.MicrometerLoggingMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(after = PlatformLoggingAutoConfiguration.class)
@ConditionalOnClass(MeterRegistry.class)
@ConditionalOnExpression(
    "${platform.logging.enabled:true} and ${platform.logging.metrics.enabled:true}")
public class LoggingMetricsAutoConfiguration {
    @Bean
    @ConditionalOnBean(MeterRegistry.class)
    @ConditionalOnMissingBean
    LoggingMetrics loggingMetrics(MeterRegistry registry) {
        return new MicrometerLoggingMetrics(registry);
    }
}
