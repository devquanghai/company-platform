package com.company.platform.integration.kafkaapp.service;

import com.company.platform.integration.kafkaapp.api.ReceivedKafkaMessage;
import com.company.platform.integration.kafkaapp.model.KafkaTestEvent;
import com.company.platform.queue.api.annotation.PlatformQueueListener;
import com.company.platform.queue.api.consume.MessageContext;
import com.company.platform.queue.api.consume.MessageHandlingResult;
import org.springframework.stereotype.Component;

@Component
public class KafkaTestListener {
    private final KafkaMessageProbe probe;

    public KafkaTestListener(KafkaMessageProbe probe) {
        this.probe = probe;
    }

    @PlatformQueueListener(
        handlerId = "kafka-test-events-handler",
        subscription = "kafka-test-events-subscription")
    public MessageHandlingResult onMessage(
        KafkaTestEvent event, MessageContext context
    ) {
        if (!valid(event)) {
            return MessageHandlingResult.REJECT;
        }
        probe.record(new ReceivedKafkaMessage(
            event,
            context.messageId(),
            context.correlationId(),
            context.physicalDestination(),
            context.partition(),
            context.offset(),
            context.deliveryAttempt(),
            context.receivedAt()));
        return MessageHandlingResult.ACK;
    }

    private boolean valid(KafkaTestEvent event) {
        return event != null
            && textWithin(event.eventId(), 128)
            && textWithin(event.aggregateId(), 128)
            && textWithin(event.message(), 4096)
            && event.createdAt() != null;
    }

    private boolean textWithin(String value, int maximumLength) {
        return value != null && !value.isBlank() && value.length() <= maximumLength;
    }
}
