package com.company.platform.queue.publish.internal.adapter.kafka;

import org.springframework.kafka.core.KafkaTemplate;

record KafkaPublisherResources(
    KafkaTemplate<String, byte[]> template
) {
}
