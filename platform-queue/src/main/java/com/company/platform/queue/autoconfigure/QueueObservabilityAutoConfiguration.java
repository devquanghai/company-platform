package com.company.platform.queue.autoconfigure;

import com.company.platform.queue.application.registry.QueueBrokerRegistry;
import com.company.platform.queue.observability.event.QueueAuditEventPublisher;
import com.company.platform.queue.observability.event.SpringQueueAuditEventPublisher;
import com.company.platform.queue.observability.health.PlatformQueueHealthIndicator;
import com.company.platform.queue.observability.metrics.MicrometerQueueMetrics;
import com.company.platform.queue.observability.metrics.QueueMetrics;
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
    after = PlatformQueueAutoConfiguration.class,
    afterName =
        "org.springframework.boot.micrometer.metrics.autoconfigure.export.simple."
            + "SimpleMetricsExportAutoConfiguration")
@ConditionalOnProperty(
    prefix = "platform.queue", name = "enabled",
    havingValue = "true", matchIfMissing = true)
public class QueueObservabilityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(
        prefix = "platform.queue.observability", name = "audit-events-enabled",
        havingValue = "true", matchIfMissing = true)
    public QueueAuditEventPublisher queueAuditEventPublisher(
        ApplicationEventPublisher publisher
    ) {
        return new SpringQueueAuditEventPublisher(publisher);
    }

    @Bean
    @ConditionalOnClass(MeterRegistry.class)
    @ConditionalOnBean(MeterRegistry.class)
    @ConditionalOnMissingBean
    @ConditionalOnProperty(
        prefix = "platform.queue.observability", name = "metrics-enabled",
        havingValue = "true", matchIfMissing = true)
    public QueueMetrics queueMetrics(MeterRegistry registry) {
        return new MicrometerQueueMetrics(registry);
    }

    @Bean("platformQueueHealthIndicator")
    @ConditionalOnClass(HealthIndicator.class)
    @ConditionalOnMissingBean(name = "platformQueueHealthIndicator")
    @ConditionalOnProperty(
        prefix = "platform.queue.observability", name = "health-enabled",
        havingValue = "true", matchIfMissing = true)
    public PlatformQueueHealthIndicator platformQueueHealthIndicator(
        QueueBrokerRegistry brokers
    ) {
        return new PlatformQueueHealthIndicator(brokers);
    }
}
