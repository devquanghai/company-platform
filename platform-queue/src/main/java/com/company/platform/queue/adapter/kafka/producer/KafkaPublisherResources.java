package com.company.platform.queue.adapter.kafka.producer;

import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;

record KafkaPublisherResources(
    DefaultKafkaProducerFactory<String, byte[]> producerFactory,
    KafkaTemplate<String, byte[]> template
) {
}
