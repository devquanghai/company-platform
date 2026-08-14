package com.company.platform.integration.queue.internal.adapter.in.kafka;

import com.company.platform.core.time.TimeProvider;
import com.company.platform.integration.queue.internal.application.QueueMessageProbe;
import com.company.platform.integration.queue.internal.domain.ConsumedQueueMessage;
import com.company.platform.integration.queue.internal.domain.QueueDemoEvent;
import com.company.platform.integration.queue.internal.domain.QueueMode;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("kafka")
public final class RealtimeQueueConsumer {
    private final QueueMessageProbe probe;
    private final KafkaMessageContextFactory contexts;

    public RealtimeQueueConsumer(QueueMessageProbe probe, TimeProvider timeProvider) {
        this.probe = probe;
        this.contexts = new KafkaMessageContextFactory(timeProvider);
    }

    @KafkaListener(
        topics = "${KAFKA_REALTIME_TOPIC:queue-realtime}",
        groupId = "${KAFKA_REALTIME_GROUP:platform-integration-realtime}",
        concurrency = "${KAFKA_REALTIME_CONCURRENCY:3}")
    public void receive(
        ConsumerRecord<String, QueueDemoEvent> record,
        Acknowledgment acknowledgment
    ) {
        var context = contexts.create(record, "queue-realtime-consumer");
        probe.record(QueueMode.REALTIME, new ConsumedQueueMessage(
            record.value(), context.messageId(), context.subscription(),
            context.physicalDestination(), context.partition(), context.offset(),
            context.deliveryAttempt(), context.receivedAt()));
        acknowledgment.acknowledge();
        log.info("Queue event consumed mode=REALTIME messageId={} partition={} offset={}",
            context.messageId(), context.partition(), context.offset());
    }
}
