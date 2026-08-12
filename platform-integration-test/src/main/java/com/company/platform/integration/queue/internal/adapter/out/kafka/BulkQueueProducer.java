package com.company.platform.integration.queue.internal.adapter.out.kafka;

import com.company.platform.integration.queue.internal.domain.QueueDemoEvent;
import com.company.platform.queue.api.kafka.BaseKafkaProducer;
import com.company.platform.queue.api.kafka.KafkaDestinationResolver;
import com.company.platform.queue.api.publish.MessagePublisher;
import com.company.platform.queue.api.publish.PublishResult;
import com.company.platform.queue.api.kafka.KafkaPublishMessage;
import com.company.platform.integration.queue.internal.domain.QueueMode;
import com.company.platform.integration.queue.internal.port.out.QueueEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Profile;
@Service
public final class BulkQueueProducer extends BaseKafkaProducer<QueueDemoEvent>
    implements QueueEventPublisher {
    public BulkQueueProducer(
        MessagePublisher publisher, KafkaDestinationResolver destinations
    ) {
        super(publisher, destinations, "queue-bulk", QueueDemoEvent.class);
    }
    @Override public QueueMode mode() { return QueueMode.BULK; }
    @Override public PublishResult publish(QueueDemoEvent event) {
        return send(KafkaPublishMessage.builder(event).key(event.businessKey())
            .messageId(event.eventId()).eventId(event.eventId())
            .correlationId(event.eventId()).eventType("QueueDemoEvent")
            .schemaVersion(1).build());
    }
}
