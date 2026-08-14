package com.company.platform.queue.internal.autoconfigure;

import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.boot.amqp.autoconfigure.RabbitProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.SimpleMessageConverter;

final class RabbitPublisherSafetyValidator {
    private final RabbitProperties properties;
    private final RabbitTemplate template;

    RabbitPublisherSafetyValidator(
        RabbitProperties properties,
        RabbitTemplate template
    ) {
        this.properties = properties;
        this.template = template;
    }

    public void validate() {
        if (properties.getPublisherConfirmType()
            != CachingConnectionFactory.ConfirmType.CORRELATED) {
            throw new QueueConfigurationException(
                "spring.rabbitmq.publisher-confirm-type must be correlated");
        }
        if (!properties.isPublisherReturns()
            || !Boolean.TRUE.equals(properties.getTemplate().getMandatory())) {
            throw new QueueConfigurationException(
                "spring.rabbitmq.publisher-returns and "
                    + "spring.rabbitmq.template.mandatory must be true");
        }
        if (template.getMessageConverter() instanceof SimpleMessageConverter) {
            throw new QueueConfigurationException(
                "RabbitMQ provider requires an application JSON or byte-array "
                    + "MessageConverter; Java serialization is forbidden");
        }
    }
}
