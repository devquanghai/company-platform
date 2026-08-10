package com.company.platform.queue.autoconfigure;

import com.company.platform.core.time.TimeProvider;
import com.company.platform.queue.publish.internal.adapter.kafka.KafkaPublisherFactory;
import com.company.platform.queue.publish.internal.adapter.kafka.NamedKafkaPublisher;
import com.company.platform.queue.consume.internal.adapter.kafka.NamedKafkaListenerContainerAdapter;
import com.company.platform.queue.consume.internal.adapter.kafka.BaseKafkaConsumerRegistrar;
import com.company.platform.queue.topology.internal.adapter.kafka.KafkaQueueTopologyManager;
import com.company.platform.queue.api.kafka.KafkaQueueOperations;
import com.company.platform.queue.api.kafka.BaseKafkaConsumer;
import com.company.platform.queue.api.kafka.KafkaPublishFailureHandler;
import com.company.platform.queue.api.kafka.KafkaDestinationResolver;
import com.company.platform.queue.api.kafka.DeferredKafkaMessageStore;
import com.company.platform.queue.consume.internal.port.out.KafkaDeadLetterPublisher;
import com.company.platform.queue.consume.internal.application.PlatformQueueListenerRegistrar;
import com.company.platform.queue.configuration.internal.registry.QueueBrokerRegistry;
import com.company.platform.queue.configuration.internal.registry.QueueSubscriptionRegistry;
import com.company.platform.queue.configuration.internal.registry.QueueDestinationRegistry;
import com.company.platform.queue.consume.internal.application.QueueMessageProcessor;
import com.company.platform.queue.autoconfigure.properties.PlatformQueueProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.kafka.core.KafkaTemplate;

@AutoConfiguration(after = PlatformQueueAutoConfiguration.class)
@ConditionalOnClass(KafkaTemplate.class)
@ConditionalOnProperty(
    prefix = "platform.queue", name = "enabled",
    havingValue = "true", matchIfMissing = true)
public class KafkaQueueAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(KafkaQueueOperations.class)
    public NamedKafkaPublisher namedKafkaPublisher(
        PlatformQueueProperties properties,
        QueueDestinationRegistry destinations,
        TimeProvider timeProvider,
        ObjectProvider<KafkaPublishFailureHandler> failureHandlers
    ) {
        return KafkaPublisherFactory.create(
            properties, destinations, timeProvider,
            failureHandlers.orderedStream().toList());
    }

    @Bean
    @ConditionalOnMissingBean(NamedKafkaListenerContainerAdapter.class)
    public NamedKafkaListenerContainerAdapter namedKafkaListenerContainerAdapter(
        PlatformQueueProperties properties,
        QueueMessageProcessor processor,
        TimeProvider timeProvider,
        ObjectProvider<DeferredKafkaMessageStore> deferredStore,
        ObjectProvider<KafkaDeadLetterPublisher> deadLetterPublisher
    ) {
        return new NamedKafkaListenerContainerAdapter(
            properties, processor, timeProvider, deferredStore.getIfAvailable(),
            deadLetterPublisher.getIfAvailable());
    }

    @Bean
    @ConditionalOnMissingBean(KafkaQueueTopologyManager.class)
    public KafkaQueueTopologyManager kafkaQueueTopologyManager(
        PlatformQueueProperties properties
    ) {
        return new KafkaQueueTopologyManager(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public KafkaDestinationResolver kafkaDestinationResolver(
        QueueDestinationRegistry destinations,
        QueueBrokerRegistry brokers
    ) {
        return name -> {
            var destination = destinations.requireEnabled(name);
            if (brokers.require(destination.getBroker()).getProvider()
                != com.company.platform.queue.domain.model.QueueProviderType.KAFKA) {
                throw new IllegalArgumentException(
                    "destination is not backed by Kafka");
            }
        };
    }

    @Bean
    @ConditionalOnMissingBean
    public BaseKafkaConsumerRegistrar baseKafkaConsumerRegistrar(
        ObjectProvider<BaseKafkaConsumer<?>> consumers,
        PlatformQueueListenerRegistrar registrar,
        QueueSubscriptionRegistry subscriptions,
        QueueDestinationRegistry destinations,
        QueueBrokerRegistry brokers
    ) {
        return new BaseKafkaConsumerRegistrar(
            consumers.orderedStream().toList(), registrar, subscriptions,
            destinations, brokers);
    }
}
