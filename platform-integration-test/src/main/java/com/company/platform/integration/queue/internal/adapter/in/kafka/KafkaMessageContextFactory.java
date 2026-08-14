package com.company.platform.integration.queue.internal.adapter.in.kafka;

import com.company.platform.core.time.TimeProvider;
import org.apache.kafka.clients.consumer.ConsumerRecord;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

final class KafkaMessageContextFactory {
    private final TimeProvider timeProvider;

    KafkaMessageContextFactory(TimeProvider timeProvider) {
        this.timeProvider = timeProvider;
    }

    Context create(ConsumerRecord<?, ?> record, String subscription) {
        String messageId = header(record, "x-message-id");
        if (messageId == null) {
            messageId = record.topic() + '-' + record.partition() + '-' + record.offset();
        }
        return new Context(subscription, record.topic(), messageId,
            timeProvider.nowInstant(), 1, record.partition(), record.offset());
    }

    private String header(ConsumerRecord<?, ?> record, String name) {
        var header = record.headers().lastHeader(name);
        return header == null ? null
            : new String(header.value(), StandardCharsets.UTF_8);
    }

    record Context(
        String subscription,
        String physicalDestination,
        String messageId,
        Instant receivedAt,
        int deliveryAttempt,
        Integer partition,
        Long offset
    ) {
    }
}
