package com.company.platform.queue.autoconfigure;

import com.company.platform.queue.consume.internal.application.DefaultPlatformQueueListenerRegistrar;
import com.company.platform.queue.consume.internal.application.PlatformQueueListenerRegistrar;
import com.company.platform.queue.configuration.internal.registry.QueueSubscriptionRegistry;
import com.company.platform.queue.configuration.internal.registry.QueueDestinationRegistry;
import com.company.platform.queue.configuration.internal.registry.QueueBrokerRegistry;
import com.company.platform.queue.consume.internal.port.out.QueueListenerContainerAdapter;
import com.company.platform.queue.consume.internal.application.QueueMessageProcessor;
import com.company.platform.queue.autoconfigure.properties.PlatformQueueProperties;
import com.company.platform.queue.reliability.inbox.InboxStore;
import com.company.platform.queue.reliability.retry.MessageRetryDecisionPolicy;
import com.company.platform.queue.serialization.registry.MessageSerializerRegistry;
import com.company.platform.queue.envelope.validation.SafeHeaderPolicy;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(after = PlatformQueueAutoConfiguration.class)
@ConditionalOnProperty(
    prefix = "platform.queue", name = "enabled",
    havingValue = "true", matchIfMissing = true)
public class QueueListenerAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public PlatformQueueListenerRegistrar platformQueueListenerRegistrar(
        QueueSubscriptionRegistry subscriptions,
        QueueDestinationRegistry destinations,
        QueueBrokerRegistry brokers,
        ObjectProvider<QueueListenerContainerAdapter> adapters
    ) {
        return new DefaultPlatformQueueListenerRegistrar(
            subscriptions, destinations, brokers, adapters.orderedStream().toList());
    }

    @Bean
    @ConditionalOnMissingBean
    public QueueMessageProcessor queueMessageProcessor(
        PlatformQueueProperties properties,
        MessageSerializerRegistry serializers,
        MessageRetryDecisionPolicy retryPolicy,
        ObjectProvider<InboxStore> inbox,
        SafeHeaderPolicy headerPolicy
    ) {
        return new QueueMessageProcessor(
            properties, serializers, retryPolicy, inbox.getIfAvailable(), headerPolicy);
    }

}
