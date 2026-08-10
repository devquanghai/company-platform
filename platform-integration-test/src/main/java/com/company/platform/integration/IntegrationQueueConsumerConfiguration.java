package com.company.platform.integration;

import com.company.platform.queue.api.consume.MessageContext;
import com.company.platform.queue.api.consume.MessageHandlingResult;
import com.company.platform.queue.api.kafka.BaseKafkaConsumer;
import com.company.platform.queue.api.rabbit.BaseRabbitConsumer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile("integration-e2e")
class IntegrationQueueConsumerConfiguration {

    @Bean
    BaseKafkaConsumer<IntegrationQueueEvent> integrationKafkaConsumer(
        IntegrationQueueListener listener
    ) {
        return new BaseKafkaConsumer<>(
            "kafka-events-handler", "integration-kafka-handler",
            IntegrationQueueEvent.class) {
            @Override
            protected MessageHandlingResult receive(
                IntegrationQueueEvent payload, MessageContext context
            ) {
                return listener.onKafka(payload, context);
            }
        };
    }

    @Bean
    BaseRabbitConsumer<IntegrationQueueEvent> integrationRabbitConsumer(
        IntegrationQueueListener listener
    ) {
        return new BaseRabbitConsumer<>(
            "rabbit-events-handler", "integration-rabbit-handler",
            IntegrationQueueEvent.class) {
            @Override
            protected MessageHandlingResult receive(
                IntegrationQueueEvent payload, MessageContext context
            ) {
                return listener.onRabbit(payload, context);
            }
        };
    }
}
