package com.company.platform.queue.configuration.internal.registry;

import com.company.platform.queue.autoconfigure.properties.DestinationProperties;

import java.util.Map;

public final class QueueDestinationRegistry {
    private final Map<String, DestinationProperties> destinations;

    public QueueDestinationRegistry(Map<String, DestinationProperties> destinations) {
        this.destinations = Map.copyOf(destinations);
    }

    public DestinationProperties requireEnabled(String name) {
        DestinationProperties destination = destinations.get(name);
        if (destination == null) {
            throw new IllegalArgumentException("unknown destination: " + safe(name));
        }
        if (!destination.isEnabled() || !destination.isProducerEnabled()) {
            throw new IllegalStateException("destination is disabled: " + safe(name));
        }
        return destination;
    }

    public DestinationProperties requireConsumerEnabled(String name) {
        DestinationProperties destination = subscriptionsValue(name);
        if (!destination.isEnabled() || !destination.isConsumerEnabled()) {
            throw new IllegalStateException("destination consumer is disabled: " + safe(name));
        }
        return destination;
    }

    public Map<String, DestinationProperties> entries() {
        return destinations;
    }

    private String safe(String value) {
        return value == null ? "<null>" : value.substring(0, Math.min(value.length(), 64));
    }

    private DestinationProperties subscriptionsValue(String name) {
        DestinationProperties destination = destinations.get(name);
        if (destination == null) {
            throw new IllegalArgumentException("unknown destination: " + safe(name));
        }
        return destination;
    }
}
