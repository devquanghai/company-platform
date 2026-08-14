package com.company.platform.integration.queue.internal.adapter.out.kafka;

import com.company.platform.integration.queue.internal.domain.QueueDemoEvent;
import com.company.platform.integration.queue.internal.domain.QueueMode;
import com.company.platform.integration.queue.internal.port.out.QueueEventPublisher;
import com.company.platform.queue.api.publish.MessagePublisher;
import com.company.platform.queue.api.publish.PublishRequest;
import com.company.platform.queue.api.publish.PublishResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
public final class RealtimeQueueProducer implements QueueEventPublisher {
    private final MessagePublisher publisher;
    private final String topic;

    public RealtimeQueueProducer(
        MessagePublisher publisher,
        @Value("${KAFKA_REALTIME_TOPIC:queue-realtime}") String topic
    ) {
        this.publisher = publisher;
        this.topic = topic;
    }

    @Override public QueueMode mode() { return QueueMode.REALTIME; }

    @Override public PublishResult publish(QueueDemoEvent event) {
        return publisher.publish(request(event, topic));
    }

    static PublishRequest<QueueDemoEvent> request(QueueDemoEvent event, String topic) {
        return PublishRequest.builder(event).destination(topic)
            .key(event.businessKey()).messageId(event.eventId())
            .eventId(event.eventId()).correlationId(event.eventId())
            .eventType("QueueDemoEvent").schemaVersion(1).build();
    }
}
