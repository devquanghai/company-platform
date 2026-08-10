package com.company.platform.queue.publish.internal.adapter.kafka;

import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;

record KafkaPublisherResources(
    DefaultKafkaProducerFactory<String, byte[]> producerFactory,
    KafkaTemplate<String, byte[]> template
) {
}
