package com.company.platform.queue.autoconfigure;

import com.company.platform.core.time.TimeProvider;
import com.company.platform.queue.configuration.internal.registry.QueueBrokerRegistry;
import com.company.platform.queue.configuration.internal.registry.QueueDestinationRegistry;
import com.company.platform.queue.publish.internal.application.MessagePublisherRegistry;
import com.company.platform.queue.autoconfigure.properties.PlatformQueueProperties;
import com.company.platform.queue.envelope.codec.MessageEnvelopeFactory;
import com.company.platform.queue.reliability.inbox.InboxStore;
import com.company.platform.queue.api.kafka.DeferredKafkaMessageStore;
import com.company.platform.queue.reliability.internal.application.DefaultTransactionalMessagePublisher;
import com.company.platform.queue.reliability.outbox.OutboxMessageStore;
import com.company.platform.queue.reliability.outbox.OutboxPollingPublisher;
import com.company.platform.queue.reliability.outbox.OutboxPollingLifecycle;
import com.company.platform.queue.reliability.outbox.TransactionalMessagePublisher;
import com.company.platform.queue.serialization.registry.MessageSerializerRegistry;
import com.company.platform.queue.configuration.internal.PlatformQueuePropertiesValidator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.amqp.autoconfigure.RabbitProperties;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(after = PlatformQueueAutoConfiguration.class)
@ConditionalOnProperty(
    prefix = "platform.queue", name = "enabled",
    havingValue = "true", matchIfMissing = true)
public class QueueReliabilityAutoConfiguration {

    @Bean(initMethod = "validate")
    @ConditionalOnMissingBean
    public PlatformQueuePropertiesValidator platformQueuePropertiesValidator(
        PlatformQueueProperties properties,
        ObjectProvider<OutboxMessageStore> outbox,
        ObjectProvider<InboxStore> inbox,
        ObjectProvider<DeferredKafkaMessageStore> deferredKafka,
        ObjectProvider<RabbitProperties> rabbitProperties,
        ObjectProvider<KafkaProperties> kafkaProperties
    ) {
        return new PlatformQueuePropertiesValidator(
            properties, outbox.getIfAvailable() != null,
            inbox.getIfAvailable() != null,
            deferredKafka.getIfAvailable() != null,
            rabbitProperties.getIfAvailable(), kafkaProperties.getIfAvailable());
    }

    @Bean
    @ConditionalOnBean(OutboxMessageStore.class)
    @ConditionalOnMissingBean
    public TransactionalMessagePublisher transactionalMessagePublisher(
        PlatformQueueProperties properties,
        OutboxMessageStore store,
        QueueDestinationRegistry destinations,
        QueueBrokerRegistry brokers,
        MessageEnvelopeFactory envelopes,
        MessageSerializerRegistry serializers,
        TimeProvider timeProvider
    ) {
        return new DefaultTransactionalMessagePublisher(
            properties, store, destinations, brokers, envelopes, serializers,
            timeProvider);
    }

    @Bean
    @ConditionalOnBean(OutboxMessageStore.class)
    @ConditionalOnMissingBean
    @ConditionalOnProperty(
        prefix = "platform.queue.delivery", name = "outbox-enabled",
        havingValue = "true")
    public OutboxPollingPublisher outboxPollingPublisher(
        PlatformQueueProperties properties,
        OutboxMessageStore store,
        QueueDestinationRegistry destinations,
        QueueBrokerRegistry brokers,
        MessagePublisherRegistry publishers,
        MessageSerializerRegistry serializers
    ) {
        return new OutboxPollingPublisher(
            properties, store, destinations, brokers, publishers, serializers);
    }

    @Bean
    @ConditionalOnBean(OutboxPollingPublisher.class)
    @ConditionalOnMissingBean
    public OutboxPollingLifecycle outboxPollingLifecycle(
        OutboxPollingPublisher publisher, PlatformQueueProperties properties
    ) {
        return new OutboxPollingLifecycle(
            publisher, properties.getDelivery().getOutboxPollInterval());
    }
}
