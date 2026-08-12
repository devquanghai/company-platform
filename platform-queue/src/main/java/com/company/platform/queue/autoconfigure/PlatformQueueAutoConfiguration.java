package com.company.platform.queue.autoconfigure;

import com.company.platform.core.context.RequestContextProvider;
import com.company.platform.core.json.JsonMapperHelper;
import com.company.platform.core.time.TimeProvider;
import com.company.platform.core.trace.TraceContextProvider;
import com.company.platform.queue.configuration.internal.registry.QueueBrokerRegistry;
import com.company.platform.queue.configuration.internal.registry.QueueDestinationRegistry;
import com.company.platform.queue.configuration.internal.registry.QueueSubscriptionRegistry;
import com.company.platform.queue.topology.internal.port.out.QueueTopologyManager;
import com.company.platform.queue.topology.internal.application.QueueTopologyLifecycle;
import com.company.platform.queue.autoconfigure.properties.PlatformQueueProperties;
import com.company.platform.queue.envelope.codec.MessageEnvelopeFactory;
import com.company.platform.queue.envelope.validation.SafeHeaderPolicy;
import com.company.platform.queue.reliability.internal.application.DefaultMessageRetryDecisionPolicy;
import com.company.platform.queue.reliability.retry.MessageRetryDecisionPolicy;
import com.company.platform.queue.serialization.MessageSerializer;
import com.company.platform.queue.serialization.json.JsonMessageSerializer;
import com.company.platform.queue.serialization.internal.adapter.DefaultMessageSerializerRegistry;
import com.company.platform.queue.serialization.registry.MessageSerializerRegistry;
import com.company.platform.queue.configuration.internal.QueueMessageDefaults;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

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
        var message = properties.getMessage();
        return new SafeHeaderPolicy(
            QueueMessageDefaults.limits(message),
            message.getAllowedHeaders());
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
        Environment environment,
        TimeProvider timeProvider,
        ObjectProvider<RequestContextProvider> requestContext,
        ObjectProvider<TraceContextProvider> traceContext,
        SafeHeaderPolicy headers
    ) {
        return new MessageEnvelopeFactory(
            environment.getProperty("spring.application.name", "unknown"),
            timeProvider, requestContext.getIfAvailable(),
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
