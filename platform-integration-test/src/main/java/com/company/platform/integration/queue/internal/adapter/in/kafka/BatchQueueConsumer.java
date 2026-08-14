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
public final class BatchQueueConsumer {
    private final QueueMessageProbe probe;
    private final KafkaMessageContextFactory contexts;

    public BatchQueueConsumer(QueueMessageProbe probe, TimeProvider timeProvider) {
        this.probe = probe;
        this.contexts = new KafkaMessageContextFactory(timeProvider);
    }

    @KafkaListener(
        topics = "${KAFKA_BATCH_TOPIC:queue-batch}",
        groupId = "${KAFKA_BATCH_GROUP:platform-integration-batch}",
        concurrency = "${KAFKA_BATCH_CONCURRENCY:1}")
    public void receive(
        ConsumerRecord<String, QueueDemoEvent> record,
        Acknowledgment acknowledgment
    ) {
        var context = contexts.create(record, "queue-batch-consumer");
        probe.record(QueueMode.BATCH, new ConsumedQueueMessage(
            record.value(), context.messageId(), context.subscription(),
            context.physicalDestination(), context.partition(), context.offset(),
            context.deliveryAttempt(), context.receivedAt()));
        acknowledgment.acknowledge();
    }
}
