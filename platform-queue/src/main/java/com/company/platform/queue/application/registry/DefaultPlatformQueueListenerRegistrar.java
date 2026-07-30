package com.company.platform.queue.application.registry;

import com.company.platform.queue.application.port.in.QueueListenerContainerAdapter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class DefaultPlatformQueueListenerRegistrar
    implements PlatformQueueListenerRegistrar {

    private final QueueSubscriptionRegistry subscriptions;
    private final QueueDestinationRegistry destinations;
    private final QueueBrokerRegistry brokers;
    private final Map<com.company.platform.queue.domain.model.QueueProviderType,
        QueueListenerContainerAdapter> adapters;
    private final Map<String, QueueListenerEndpoint> endpoints =
        new ConcurrentHashMap<>();

    public DefaultPlatformQueueListenerRegistrar(
        QueueSubscriptionRegistry subscriptions
    ) {
        this(subscriptions, null, null, List.of());
    }

    public DefaultPlatformQueueListenerRegistrar(
        QueueSubscriptionRegistry subscriptions,
        QueueDestinationRegistry destinations,
        QueueBrokerRegistry brokers,
        List<QueueListenerContainerAdapter> adapters
    ) {
        this.subscriptions = subscriptions;
        this.destinations = destinations;
        this.brokers = brokers;
        this.adapters = adapters.stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
            QueueListenerContainerAdapter::provider, adapter -> adapter,
            (first, duplicate) -> {
                throw new IllegalArgumentException(
                    "duplicate listener adapter for " + first.provider());
            }));
    }

    @Override
    public void register(QueueListenerEndpoint endpoint) {
        var subscription = subscriptions.requireEnabled(endpoint.subscription());
        if (endpoints.putIfAbsent(endpoint.handlerId(), endpoint) != null) {
            throw new IllegalStateException(
                "duplicate queue listener handler-id: " + endpoint.handlerId());
        }
        if (destinations != null && brokers != null) {
            var destination = destinations.requireConsumerEnabled(
                subscription.getDestination());
            var provider = brokers.require(destination.getBroker()).getProvider();
            var adapter = adapters.get(provider);
            if (adapter == null) {
                endpoints.remove(endpoint.handlerId(), endpoint);
                throw new IllegalStateException(
                    "no listener container adapter for provider " + provider);
            }
            adapter.register(endpoint, subscription, destination);
        }
    }

    public Map<String, QueueListenerEndpoint> endpoints() {
        return Map.copyOf(endpoints);
    }
}
