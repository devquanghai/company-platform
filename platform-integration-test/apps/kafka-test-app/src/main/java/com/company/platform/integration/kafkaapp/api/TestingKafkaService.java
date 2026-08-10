package com.company.platform.integration.kafkaapp.api;

import com.company.platform.integration.kafkaapp.model.KafkaTestEvent;
import com.company.platform.queue.api.kafka.BaseKafkaProducer;
import com.company.platform.queue.api.kafka.KafkaDestinationResolver;
import com.company.platform.queue.api.kafka.KafkaPublishMessage;
import com.company.platform.queue.api.publish.MessagePublisher;
import com.company.platform.queue.api.publish.PublishResult;

public class TestingKafkaService extends BaseKafkaProducer<KafkaTestEvent> {

    protected TestingKafkaService(MessagePublisher publisher, KafkaDestinationResolver destinationResolver, String destination, Class<KafkaTestEvent> payloadType) {
        super(publisher, destinationResolver, destination, payloadType);
    }


}
