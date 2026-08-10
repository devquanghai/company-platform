package com.company.platform.queue.configuration.internal.registry;

import com.company.platform.queue.autoconfigure.properties.BrokerProperties;

import java.util.Map;

public final class QueueBrokerRegistry {
    private final Map<String, BrokerProperties> brokers;

    public QueueBrokerRegistry(Map<String, BrokerProperties> brokers) {
        this.brokers = Map.copyOf(brokers);
    }

    public BrokerProperties require(String name) {
        BrokerProperties broker = brokers.get(name);
        if (broker == null) {
            throw new IllegalArgumentException("unknown broker: " + safe(name));
        }
        return broker;
    }

    public Map<String, BrokerProperties> entries() {
        return brokers;
    }

    private String safe(String value) {
        return value == null ? "<null>" : value.substring(0, Math.min(value.length(), 64));
    }
}
