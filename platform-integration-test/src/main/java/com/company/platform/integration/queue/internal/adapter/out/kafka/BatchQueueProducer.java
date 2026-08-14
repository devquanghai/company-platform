package com.company.platform.integration.queue.internal.adapter.out.kafka;

import com.company.platform.integration.queue.internal.domain.QueueDemoEvent;
import com.company.platform.integration.queue.internal.domain.QueueMode;
import com.company.platform.integration.queue.internal.port.out.QueueEventPublisher;
import com.company.platform.queue.api.publish.MessagePublisher;
import com.company.platform.queue.api.publish.PublishResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
public final class BatchQueueProducer implements QueueEventPublisher {
    private final MessagePublisher publisher;
    private final String topic;

    public BatchQueueProducer(
        MessagePublisher publisher,
        @Value("${KAFKA_BATCH_TOPIC:queue-batch}") String topic
    ) { this.publisher = publisher; this.topic = topic; }

    @Override public QueueMode mode() { return QueueMode.BATCH; }
    @Override public PublishResult publish(QueueDemoEvent event) {
        return publisher.publish(RealtimeQueueProducer.request(event, topic));
    }
}
