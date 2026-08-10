package com.company.platform.queue.consume.internal.adapter.rabbit;

import com.company.platform.queue.api.rabbit.BaseRabbitConsumer;
import com.company.platform.queue.configuration.internal.registry.QueueBrokerRegistry;
import com.company.platform.queue.configuration.internal.registry.QueueDestinationRegistry;
import com.company.platform.queue.configuration.internal.registry.QueueSubscriptionRegistry;
import com.company.platform.queue.consume.internal.application.PlatformQueueListenerRegistrar;
import com.company.platform.queue.consume.internal.application.QueueListenerEndpoint;
import com.company.platform.queue.domain.model.QueueProviderType;
import org.springframework.beans.factory.SmartInitializingSingleton;

import java.util.List;
import java.util.Objects;

public final class BaseRabbitConsumerRegistrar implements SmartInitializingSingleton {
    private final List<BaseRabbitConsumer<?>> consumers;
    private final PlatformQueueListenerRegistrar registrar;
    private final QueueSubscriptionRegistry subscriptions;
    private final QueueDestinationRegistry destinations;
    private final QueueBrokerRegistry brokers;

    public BaseRabbitConsumerRegistrar(
        List<BaseRabbitConsumer<?>> consumers,
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

    private void register(BaseRabbitConsumer<?> consumer) {
        var subscription = subscriptions.requireEnabled(consumer.subscription());
        var destination = destinations.requireConsumerEnabled(subscription.getDestination());
        if (brokers.require(destination.getBroker()).getProvider() != QueueProviderType.RABBITMQ) {
            throw new IllegalStateException(
                "BaseRabbitConsumer subscription must use a RabbitMQ broker");
        }
        registrar.register(new QueueListenerEndpoint(
            consumer.handlerId(), consumer.subscription(), consumer,
            consumer.invocationMethod(), consumer.payloadType()));
    }
}
