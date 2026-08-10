package com.company.platform.queue.consume.internal.adapter.kafka;

final class KafkaRetryRequiredException extends RuntimeException {
    KafkaRetryRequiredException(String subscription) {
        super("Kafka listener retry required for subscription " + subscription);
    }
}
