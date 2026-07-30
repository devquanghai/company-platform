package com.company.platform.queue.api.kafka;

import java.time.Duration;

public interface KafkaQueueOperations extends KafkaTransactionalOperations {
    void flush(String brokerName);
    int partitionCount(String brokerName, String topic, Duration timeout);
}
