package com.company.platform.integration.queue.internal.adapter.in.kafka;

import com.company.platform.core.time.TimeProvider;
import com.company.platform.integration.queue.internal.application.QueueMessageProbe;
import com.company.platform.integration.queue.internal.domain.ConsumedQueueMessage;
import com.company.platform.integration.queue.internal.domain.QueueDemoEvent;
import com.company.platform.integration.queue.internal.domain.QueueMode;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
@Profile("kafka")
public final class BulkQueueConsumer {
    private final QueueMessageProbe probe;
    private final KafkaMessageContextFactory contexts;

    public BulkQueueConsumer(QueueMessageProbe probe, TimeProvider timeProvider) {
        this.probe = probe;
        this.contexts = new KafkaMessageContextFactory(timeProvider);
    }

    @KafkaListener(
        topics = "${KAFKA_BULK_TOPIC:queue-bulk}",
        groupId = "${KAFKA_BULK_GROUP:platform-integration-bulk}",
        concurrency = "${KAFKA_BULK_CONCURRENCY:1}")
    public void receive(
        ConsumerRecord<String, QueueDemoEvent> record,
        Acknowledgment acknowledgment
    ) {
        var context = contexts.create(record, "queue-bulk-consumer");
        probe.record(QueueMode.BULK, new ConsumedQueueMessage(
            record.value(), context.messageId(), context.subscription(),
            context.physicalDestination(), context.partition(), context.offset(),
            context.deliveryAttempt(), context.receivedAt()));
        acknowledgment.acknowledge();
    }
}
