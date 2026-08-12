package com.company.platform.integration.queue.internal.application;

import com.company.platform.core.time.TimeProvider;
import com.company.platform.integration.queue.internal.domain.QueueDemoEvent;
import com.company.platform.integration.queue.internal.domain.QueueMode;
import com.company.platform.integration.queue.internal.port.out.QueueEventPublisher;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class QueuePublishService {
    private final Map<QueueMode, QueueEventPublisher> publishers;
    private final TimeProvider time;

    public QueuePublishService(List<QueueEventPublisher> publishers, TimeProvider time) {
        this.publishers = new EnumMap<>(QueueMode.class);
        publishers.forEach(publisher -> {
            if (this.publishers.putIfAbsent(publisher.mode(), publisher) != null) {
                throw new IllegalArgumentException("duplicate queue producer mode");
            }
        });
        this.time = time;
    }

    public QueuePublishOutcome publish(
        QueueMode mode, String businessKey, String message,
        Map<String, String> attributes
    ) {
        QueueDemoEvent event = new QueueDemoEvent(
            UUID.randomUUID().toString(), mode, businessKey, message,
            attributes == null ? Map.of() : attributes, time.nowInstant());
        QueueEventPublisher publisher = publishers.get(mode);
        if (publisher == null) {
            throw new IllegalStateException("queue producer mode is unavailable");
        }
        return new QueuePublishOutcome(event, publisher.publish(event));
    }
}
