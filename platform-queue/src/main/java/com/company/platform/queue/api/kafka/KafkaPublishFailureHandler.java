package com.company.platform.queue.api.kafka;

@FunctionalInterface
public interface KafkaPublishFailureHandler {
    void onFailure(KafkaPublishFailure failure);
}
