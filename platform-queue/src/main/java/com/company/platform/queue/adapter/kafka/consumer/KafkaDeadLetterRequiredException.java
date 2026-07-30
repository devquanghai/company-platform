package com.company.platform.queue.adapter.kafka.consumer;

public final class KafkaDeadLetterRequiredException extends RuntimeException {
    public KafkaDeadLetterRequiredException(
        String subscription, String topic, int partition, long offset
    ) {
        super("Kafka record requires configured DLT handoff: subscription="
            + subscription + ", topic=" + topic + ", partition=" + partition
            + ", offset=" + offset);
    }
}
