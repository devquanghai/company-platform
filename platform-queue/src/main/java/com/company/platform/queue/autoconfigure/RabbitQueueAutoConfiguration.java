package com.company.platform.queue.autoconfigure;

import com.company.platform.core.time.TimeProvider;
import com.company.platform.queue.publish.internal.adapter.rabbit.NamedRabbitPublisher;
import com.company.platform.queue.publish.internal.adapter.rabbit.RabbitPublisherFactory;
import com.company.platform.queue.consume.internal.adapter.rabbit.NamedRabbitListenerContainerAdapter;
import com.company.platform.queue.consume.internal.adapter.rabbit.BaseRabbitConsumerRegistrar;
import com.company.platform.queue.topology.internal.adapter.rabbit.RabbitQueueTopologyManager;
import com.company.platform.queue.api.rabbit.RabbitQueueOperations;
import com.company.platform.queue.api.rabbit.BaseRabbitConsumer;
import com.company.platform.queue.configuration.internal.registry.QueueBrokerRegistry;
import com.company.platform.queue.configuration.internal.registry.QueueDestinationRegistry;
import com.company.platform.queue.configuration.internal.registry.QueueSubscriptionRegistry;
import com.company.platform.queue.consume.internal.application.PlatformQueueListenerRegistrar;
import com.company.platform.queue.consume.internal.application.QueueMessageProcessor;
import com.company.platform.queue.autoconfigure.properties.PlatformQueueProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.ObjectProvider;

@AutoConfiguration(
    after = PlatformQueueAutoConfiguration.class,
    afterName = "org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration")
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
        TimeProvider timeProvider,
        RabbitTemplate rabbitTemplate
    ) {
        return RabbitPublisherFactory.create(
            properties, destinations, timeProvider, rabbitTemplate);
    }

    @Bean
    @ConditionalOnMissingBean(NamedRabbitListenerContainerAdapter.class)
    public NamedRabbitListenerContainerAdapter namedRabbitListenerContainerAdapter(
        PlatformQueueProperties properties,
        QueueMessageProcessor processor,
        TimeProvider timeProvider,
        SimpleRabbitListenerContainerFactory containerFactory
    ) {
        return new NamedRabbitListenerContainerAdapter(
            properties, processor, timeProvider, containerFactory);
    }

    @Bean
    @ConditionalOnMissingBean(RabbitQueueTopologyManager.class)
    @ConditionalOnBean(AmqpAdmin.class)
    public RabbitQueueTopologyManager rabbitQueueTopologyManager(
        PlatformQueueProperties properties,
        AmqpAdmin amqpAdmin
    ) {
        return new RabbitQueueTopologyManager(properties, amqpAdmin);
    }

    @Bean
    @ConditionalOnMissingBean
    public BaseRabbitConsumerRegistrar baseRabbitConsumerRegistrar(
        ObjectProvider<BaseRabbitConsumer<?>> consumers,
        PlatformQueueListenerRegistrar registrar,
        QueueSubscriptionRegistry subscriptions,
        QueueDestinationRegistry destinations,
        QueueBrokerRegistry brokers
    ) {
        return new BaseRabbitConsumerRegistrar(
            consumers.orderedStream().toList(), registrar, subscriptions,
            destinations, brokers);
    }
}
