package com.company.platform.queue.api.kafka;

@FunctionalInterface
public interface KafkaDestinationResolver {
    void requireKafkaDestination(String destination);
}
