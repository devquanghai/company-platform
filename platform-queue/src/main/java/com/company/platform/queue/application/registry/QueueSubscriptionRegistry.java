package com.company.platform.queue.application.registry;

import com.company.platform.queue.autoconfigure.properties.SubscriptionProperties;

import java.util.Map;

public final class QueueSubscriptionRegistry {
    private final Map<String, SubscriptionProperties> subscriptions;

    public QueueSubscriptionRegistry(Map<String, SubscriptionProperties> subscriptions) {
        this.subscriptions = Map.copyOf(subscriptions);
    }

    public SubscriptionProperties requireEnabled(String name) {
        SubscriptionProperties subscription = subscriptions.get(name);
        if (subscription == null) {
            throw new IllegalArgumentException("unknown subscription: " + safe(name));
        }
        if (!subscription.isEnabled()) {
            throw new IllegalStateException("subscription is disabled: " + safe(name));
        }
        return subscription;
    }

    public Map<String, SubscriptionProperties> entries() {
        return subscriptions;
    }

    private String safe(String value) {
        return value == null ? "<null>" : value.substring(0, Math.min(value.length(), 64));
    }
}
