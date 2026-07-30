package com.company.platform.queue.autoconfigure;

import com.company.platform.core.time.TimeProvider;
import com.company.platform.queue.adapter.rabbit.producer.NamedRabbitPublisher;
import com.company.platform.queue.adapter.rabbit.producer.RabbitPublisherFactory;
import com.company.platform.queue.adapter.rabbit.consumer.NamedRabbitListenerContainerAdapter;
import com.company.platform.queue.adapter.rabbit.topology.RabbitQueueTopologyManager;
import com.company.platform.queue.api.rabbit.RabbitQueueOperations;
import com.company.platform.queue.application.registry.QueueDestinationRegistry;
import com.company.platform.queue.application.service.QueueMessageProcessor;
import com.company.platform.queue.autoconfigure.properties.PlatformQueueProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(after = PlatformQueueAutoConfiguration.class)
@ConditionalOnClass(RabbitTemplate.class)
@ConditionalOnProperty(
    prefix = "platform.queue", name = "enabled",
    havingValue = "true", matchIfMissing = true)
public class RabbitQueueAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(RabbitQueueOperations.class)
    public NamedRabbitPublisher namedRabbitPublisher(
        PlatformQueueProperties properties,
        QueueDestinationRegistry destinations,
        TimeProvider timeProvider
    ) {
        return RabbitPublisherFactory.create(properties, destinations, timeProvider);
    }

    @Bean
    @ConditionalOnMissingBean(NamedRabbitListenerContainerAdapter.class)
    public NamedRabbitListenerContainerAdapter namedRabbitListenerContainerAdapter(
        PlatformQueueProperties properties,
        QueueMessageProcessor processor,
        TimeProvider timeProvider
    ) {
        return new NamedRabbitListenerContainerAdapter(
            properties, processor, timeProvider);
    }

    @Bean
    @ConditionalOnMissingBean(RabbitQueueTopologyManager.class)
    public RabbitQueueTopologyManager rabbitQueueTopologyManager(
        PlatformQueueProperties properties
    ) {
        return new RabbitQueueTopologyManager(properties);
    }
}
