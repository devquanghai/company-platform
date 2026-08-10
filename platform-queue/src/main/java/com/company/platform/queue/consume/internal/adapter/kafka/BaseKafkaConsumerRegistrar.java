package com.company.platform.queue.consume.internal.adapter.kafka;

import com.company.platform.queue.api.kafka.BaseKafkaConsumer;
import com.company.platform.queue.consume.internal.application.PlatformQueueListenerRegistrar;
import com.company.platform.queue.configuration.internal.registry.QueueBrokerRegistry;
import com.company.platform.queue.configuration.internal.registry.QueueDestinationRegistry;
import com.company.platform.queue.consume.internal.application.QueueListenerEndpoint;
import com.company.platform.queue.configuration.internal.registry.QueueSubscriptionRegistry;
import com.company.platform.queue.domain.model.QueueProviderType;
import org.springframework.beans.factory.SmartInitializingSingleton;

import java.util.List;
import java.util.Objects;

public final class BaseKafkaConsumerRegistrar implements SmartInitializingSingleton {
    private final List<BaseKafkaConsumer<?>> consumers;
    private final PlatformQueueListenerRegistrar registrar;
    private final QueueSubscriptionRegistry subscriptions;
    private final QueueDestinationRegistry destinations;
    private final QueueBrokerRegistry brokers;

    public BaseKafkaConsumerRegistrar(
        List<BaseKafkaConsumer<?>> consumers,
        PlatformQueueListenerRegistrar registrar,
        QueueSubscriptionRegistry subscriptions,
        QueueDestinationRegistry destinations,
        QueueBrokerRegistry brokers
    ) {
        this.consumers = List.copyOf(consumers);
        this.registrar = Objects.requireNonNull(registrar, "registrar");
        this.subscriptions = Objects.requireNonNull(subscriptions, "subscriptions");
        this.destinations = Objects.requireNonNull(destinations, "destinations");
        this.brokers = Objects.requireNonNull(brokers, "brokers");
    }

    @Override
    public void afterSingletonsInstantiated() {
        consumers.forEach(this::register);
    }

    private void register(BaseKafkaConsumer<?> consumer) {
        var subscription = subscriptions.entries().get(consumer.subscription());
        if (subscription == null) {
            throw new IllegalArgumentException(
                "unknown subscription: " + consumer.subscription());
        }
        if (!subscription.isEnabled()) {
            return;
        }
        var destination = destinations.entries().get(subscription.getDestination());
        if (destination == null) {
            throw new IllegalArgumentException(
                "unknown destination: " + subscription.getDestination());
        }
        if (!destination.isEnabled() || !destination.isConsumerEnabled()) {
            return;
        }
        if (brokers.require(destination.getBroker()).getProvider()
            != QueueProviderType.KAFKA) {
            throw new IllegalStateException(
                "BaseKafkaConsumer subscription must use a Kafka broker");
        }
        registrar.register(new QueueListenerEndpoint(
            consumer.handlerId(), consumer.subscription(), consumer,
            consumer.invocationMethod(), consumer.payloadType()));
    }
}
