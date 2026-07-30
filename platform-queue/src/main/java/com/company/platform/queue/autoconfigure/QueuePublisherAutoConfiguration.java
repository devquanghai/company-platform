package com.company.platform.queue.autoconfigure;

import com.company.platform.queue.api.publish.MessagePublisher;
import com.company.platform.queue.api.publish.TypedMessagePublisherFactory;
import com.company.platform.queue.application.port.out.ProviderMessagePublisher;
import com.company.platform.queue.application.registry.MessagePublisherRegistry;
import com.company.platform.queue.application.registry.QueueBrokerRegistry;
import com.company.platform.queue.application.registry.QueueDestinationRegistry;
import com.company.platform.queue.application.service.DefaultMessagePublisher;
import com.company.platform.queue.application.service.DefaultTypedMessagePublisherFactory;
import com.company.platform.queue.autoconfigure.properties.PlatformQueueProperties;
import com.company.platform.queue.envelope.codec.MessageEnvelopeFactory;
import com.company.platform.queue.reliability.outbox.TransactionalMessagePublisher;
import com.company.platform.queue.serialization.registry.MessageSerializerRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(after = {
    PlatformQueueAutoConfiguration.class,
    KafkaQueueAutoConfiguration.class,
    RabbitQueueAutoConfiguration.class,
    QueueReliabilityAutoConfiguration.class
})
@ConditionalOnProperty(
    prefix = "platform.queue", name = "enabled",
    havingValue = "true", matchIfMissing = true)
public class QueuePublisherAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public MessagePublisherRegistry messagePublisherRegistry(
        ObjectProvider<ProviderMessagePublisher> publishers
    ) {
        return new MessagePublisherRegistry(publishers.orderedStream().toList());
    }

    @Bean
    @ConditionalOnMissingBean
    public MessagePublisher messagePublisher(
        PlatformQueueProperties properties,
        QueueBrokerRegistry brokers,
        QueueDestinationRegistry destinations,
        MessagePublisherRegistry publishers,
        MessageEnvelopeFactory envelopes,
        MessageSerializerRegistry serializers,
        ObjectProvider<TransactionalMessagePublisher> outbox
    ) {
        return new DefaultMessagePublisher(
            properties, brokers, destinations, publishers, envelopes, serializers,
            outbox.getIfAvailable());
    }

    @Bean
    @ConditionalOnMissingBean
    public TypedMessagePublisherFactory typedMessagePublisherFactory(
        MessagePublisher publisher
    ) {
        return new DefaultTypedMessagePublisherFactory(publisher);
    }
}
