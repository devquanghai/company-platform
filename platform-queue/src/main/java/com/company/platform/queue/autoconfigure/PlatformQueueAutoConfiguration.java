package com.company.platform.queue.autoconfigure;

import com.company.platform.core.context.RequestContextProvider;
import com.company.platform.core.json.JsonMapperHelper;
import com.company.platform.core.time.TimeProvider;
import com.company.platform.core.trace.TraceContextProvider;
import com.company.platform.queue.application.registry.QueueBrokerRegistry;
import com.company.platform.queue.application.registry.QueueDestinationRegistry;
import com.company.platform.queue.application.registry.QueueSubscriptionRegistry;
import com.company.platform.queue.application.port.out.QueueTopologyManager;
import com.company.platform.queue.application.service.QueueTopologyLifecycle;
import com.company.platform.queue.autoconfigure.properties.PlatformQueueProperties;
import com.company.platform.queue.envelope.codec.MessageEnvelopeFactory;
import com.company.platform.queue.envelope.validation.MessageLimits;
import com.company.platform.queue.envelope.validation.SafeHeaderPolicy;
import com.company.platform.queue.reliability.retry.DefaultMessageRetryDecisionPolicy;
import com.company.platform.queue.reliability.retry.MessageRetryDecisionPolicy;
import com.company.platform.queue.serialization.MessageSerializer;
import com.company.platform.queue.serialization.json.JsonMessageSerializer;
import com.company.platform.queue.serialization.registry.DefaultMessageSerializerRegistry;
import com.company.platform.queue.serialization.registry.MessageSerializerRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@EnableConfigurationProperties(PlatformQueueProperties.class)
@ConditionalOnProperty(
    prefix = "platform.queue", name = "enabled",
    havingValue = "true", matchIfMissing = true)
public class PlatformQueueAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public QueueBrokerRegistry queueBrokerRegistry(PlatformQueueProperties properties) {
        return new QueueBrokerRegistry(properties.getBrokers());
    }

    @Bean
    @ConditionalOnMissingBean
    public QueueDestinationRegistry queueDestinationRegistry(
        PlatformQueueProperties properties
    ) {
        return new QueueDestinationRegistry(properties.getDestinations());
    }

    @Bean
    @ConditionalOnMissingBean
    public QueueSubscriptionRegistry queueSubscriptionRegistry(
        PlatformQueueProperties properties
    ) {
        return new QueueSubscriptionRegistry(properties.getSubscriptions());
    }

    @Bean
    @ConditionalOnMissingBean
    public SafeHeaderPolicy safeHeaderPolicy(PlatformQueueProperties properties) {
        var defaults = properties.getDefaults();
        return new SafeHeaderPolicy(
            new MessageLimits(
                defaults.getMaxHeaders(), defaults.getMaxHeaderBytes(),
                defaults.getMaxTotalHeaderBytes(), defaults.getMaxPayloadBytes(),
                defaults.getMaxEnvelopeBytes()),
            defaults.getAllowedCustomHeaders());
    }

    @Bean
    @ConditionalOnMissingBean
    public JsonMessageSerializer jsonMessageSerializer(JsonMapperHelper json) {
        return new JsonMessageSerializer(json);
    }

    @Bean
    @ConditionalOnMissingBean
    public MessageSerializerRegistry messageSerializerRegistry(
        ObjectProvider<MessageSerializer> serializers
    ) {
        return new DefaultMessageSerializerRegistry(
            serializers.orderedStream().toList());
    }

    @Bean
    @ConditionalOnMissingBean
    public MessageEnvelopeFactory messageEnvelopeFactory(
        PlatformQueueProperties properties,
        TimeProvider timeProvider,
        ObjectProvider<RequestContextProvider> requestContext,
        ObjectProvider<TraceContextProvider> traceContext,
        SafeHeaderPolicy headers
    ) {
        return new MessageEnvelopeFactory(
            properties, timeProvider, requestContext.getIfAvailable(),
            traceContext.getIfAvailable(), headers);
    }

    @Bean
    @ConditionalOnMissingBean
    public MessageRetryDecisionPolicy messageRetryDecisionPolicy() {
        return new DefaultMessageRetryDecisionPolicy();
    }

    @Bean
    @ConditionalOnMissingBean
    public QueueTopologyLifecycle queueTopologyLifecycle(
        PlatformQueueProperties properties,
        ObjectProvider<QueueTopologyManager> managers
    ) {
        return new QueueTopologyLifecycle(
            properties, managers.orderedStream().toList());
    }
}
