package com.company.platform.integration.queue.internal.adapter.in.kafka;

import com.company.platform.integration.queue.internal.domain.ConsumedQueueMessage;
import com.company.platform.integration.queue.internal.application.QueueMessageProbe;
import com.company.platform.integration.queue.internal.domain.QueueDemoEvent;
import com.company.platform.integration.queue.internal.domain.QueueMode;
import com.company.platform.queue.api.consume.MessageContext;
import com.company.platform.queue.api.consume.MessageHandlingResult;
import com.company.platform.queue.api.kafka.BaseKafkaConsumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;

@Slf4j
@Component
public final class BatchQueueConsumer extends BaseKafkaConsumer<QueueDemoEvent> {
    private final QueueMessageProbe probe;

    public BatchQueueConsumer(QueueMessageProbe probe) {
        super("queue-batch-consumer", "integration-batch-handler", QueueDemoEvent.class);
        this.probe = probe;
    }

    @Override
    protected MessageHandlingResult receive(QueueDemoEvent event, MessageContext context) {
        probe.record(QueueMode.BATCH, new ConsumedQueueMessage(
            event, context.messageId(), context.subscription(), context.physicalDestination(),
            context.partition(), context.offset(), context.deliveryAttempt(), context.receivedAt()));
        log.info("Queue event consumed mode=BATCH messageId={} partition={} offset={}",
            context.messageId(), context.partition(), context.offset());
        return MessageHandlingResult.ACK;
    }
}
