package com.company.platform.queue.autoconfigure;

import com.company.platform.core.time.TimeProvider;
import com.company.platform.queue.adapter.kafka.producer.KafkaPublisherFactory;
import com.company.platform.queue.adapter.kafka.producer.NamedKafkaPublisher;
import com.company.platform.queue.adapter.kafka.consumer.NamedKafkaListenerContainerAdapter;
import com.company.platform.queue.adapter.kafka.topology.KafkaQueueTopologyManager;
import com.company.platform.queue.api.kafka.KafkaQueueOperations;
import com.company.platform.queue.application.registry.QueueDestinationRegistry;
import com.company.platform.queue.application.service.QueueMessageProcessor;
import com.company.platform.queue.autoconfigure.properties.PlatformQueueProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
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
        TimeProvider timeProvider
    ) {
        return KafkaPublisherFactory.create(properties, destinations, timeProvider);
    }

    @Bean
    @ConditionalOnMissingBean(NamedKafkaListenerContainerAdapter.class)
    public NamedKafkaListenerContainerAdapter namedKafkaListenerContainerAdapter(
        PlatformQueueProperties properties,
        QueueMessageProcessor processor,
        TimeProvider timeProvider
    ) {
        return new NamedKafkaListenerContainerAdapter(
            properties, processor, timeProvider);
    }

    @Bean
    @ConditionalOnMissingBean(KafkaQueueTopologyManager.class)
    public KafkaQueueTopologyManager kafkaQueueTopologyManager(
        PlatformQueueProperties properties
    ) {
        return new KafkaQueueTopologyManager(properties);
    }
}
