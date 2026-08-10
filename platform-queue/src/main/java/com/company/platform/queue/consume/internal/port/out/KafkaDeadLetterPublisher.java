package com.company.platform.queue.consume.internal.port.out;

import org.apache.kafka.common.header.Headers;

import java.time.Duration;

public interface KafkaDeadLetterPublisher {
    void publishDeadLetter(
        String brokerName,
        String topic,
        int partition,
        String key,
        byte[] body,
        Headers headers,
        Duration timeout);
}
