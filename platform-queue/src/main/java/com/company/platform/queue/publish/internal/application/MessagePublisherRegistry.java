package com.company.platform.queue.publish.internal.application;

import com.company.platform.queue.publish.internal.port.out.ProviderMessagePublisher;
import com.company.platform.queue.domain.model.QueueProviderType;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class MessagePublisherRegistry {
    private final Map<QueueProviderType, ProviderMessagePublisher> publishers;

    public MessagePublisherRegistry(List<ProviderMessagePublisher> publishers) {
        EnumMap<QueueProviderType, ProviderMessagePublisher> values =
            new EnumMap<>(QueueProviderType.class);
        for (ProviderMessagePublisher publisher : publishers) {
            if (values.put(publisher.provider(), publisher) != null) {
                throw new IllegalArgumentException(
                    "duplicate publisher for " + publisher.provider());
            }
        }
        this.publishers = Map.copyOf(values);
    }

    public ProviderMessagePublisher require(QueueProviderType provider) {
        ProviderMessagePublisher publisher = publishers.get(provider);
        if (publisher == null) {
            throw new IllegalStateException("publisher is unavailable for " + provider);
        }
        return publisher;
    }
}
