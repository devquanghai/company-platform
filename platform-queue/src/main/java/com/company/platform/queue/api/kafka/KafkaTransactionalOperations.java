package com.company.platform.queue.api.kafka;

import java.util.function.Supplier;

public interface KafkaTransactionalOperations {
    <T> T executeInTransaction(String brokerName, Supplier<T> action);
}
