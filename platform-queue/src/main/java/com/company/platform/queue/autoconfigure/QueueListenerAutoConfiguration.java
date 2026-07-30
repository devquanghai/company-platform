package com.company.platform.queue.autoconfigure;

import com.company.platform.queue.application.registry.DefaultPlatformQueueListenerRegistrar;
import com.company.platform.queue.application.registry.PlatformQueueListenerBeanPostProcessor;
import com.company.platform.queue.application.registry.PlatformQueueListenerRegistrar;
import com.company.platform.queue.application.registry.QueueSubscriptionRegistry;
import com.company.platform.queue.application.registry.QueueDestinationRegistry;
import com.company.platform.queue.application.registry.QueueBrokerRegistry;
import com.company.platform.queue.application.port.in.QueueListenerContainerAdapter;
import com.company.platform.queue.application.service.QueueMessageProcessor;
import com.company.platform.queue.autoconfigure.properties.PlatformQueueProperties;
import com.company.platform.queue.reliability.inbox.InboxStore;
import com.company.platform.queue.reliability.retry.MessageRetryDecisionPolicy;
import com.company.platform.queue.serialization.registry.MessageSerializerRegistry;
import org.springframework.beans.factory.ObjectProvider;
import com.company.platform.queue.application.resolver.PlatformQueueListenerMetadataResolver;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(after = PlatformQueueAutoConfiguration.class)
@ConditionalOnProperty(
    prefix = "platform.queue", name = {"enabled", "annotations-enabled"},
    havingValue = "true", matchIfMissing = true)
public class QueueListenerAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public PlatformQueueListenerMetadataResolver queueListenerMetadataResolver() {
        return new PlatformQueueListenerMetadataResolver();
    }

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
        ObjectProvider<InboxStore> inbox
    ) {
        return new QueueMessageProcessor(
            properties, serializers, retryPolicy, inbox.getIfAvailable());
    }

    @Bean
    @ConditionalOnMissingBean
    public static PlatformQueueListenerBeanPostProcessor
        platformQueueListenerBeanPostProcessor(
            PlatformQueueListenerMetadataResolver resolver,
            PlatformQueueListenerRegistrar registrar
        ) {
        return new PlatformQueueListenerBeanPostProcessor(resolver, registrar);
    }
}
