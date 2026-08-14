package com.company.platform.queue.internal.autoconfigure;

import com.company.platform.core.time.TimeProvider;
import com.company.platform.queue.api.publish.MessagePublisher;
import com.company.platform.queue.internal.publish.adapter.rabbit.RabbitMqMessagePublisher;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.amqp.autoconfigure.RabbitProperties;
import org.springframework.beans.factory.ObjectProvider;

@AutoConfiguration(
    after = PlatformQueueAutoConfiguration.class,
    afterName = "org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration")
@ConditionalOnClass(RabbitTemplate.class)
@ConditionalOnProperty(
    prefix = "platform.queue", name = "enabled",
    havingValue = "true", matchIfMissing = true)
@ConditionalOnProperty(
    prefix = "platform.queue", name = "provider", havingValue = "rabbitmq")
public class RabbitMqPlatformQueueAutoConfiguration {

    @Bean(initMethod = "validate")
    @ConditionalOnMissingBean(MessagePublisher.class)
    RabbitPublisherSafetyValidator rabbitPublisherSafetyValidator(
        ObjectProvider<RabbitProperties> properties,
        ObjectProvider<RabbitTemplate> rabbitTemplates
    ) {
        RabbitProperties rabbitProperties = properties.getIfUnique();
        RabbitTemplate rabbitTemplate = rabbitTemplates.getIfUnique();
        if (rabbitProperties == null || rabbitTemplate == null) {
            throw new QueueConfigurationException(
                "platform.queue.provider=RABBITMQ requires one Boot-managed RabbitTemplate");
        }
        return new RabbitPublisherSafetyValidator(rabbitProperties, rabbitTemplate);
    }

    @Bean
    @ConditionalOnMissingBean(MessagePublisher.class)
    MessagePublisher rabbitMqMessagePublisher(
        ObjectProvider<RabbitTemplate> rabbitTemplates,
        TimeProvider timeProvider,
        RabbitPublisherSafetyValidator safetyValidator
    ) {
        RabbitTemplate rabbitTemplate = rabbitTemplates.getIfUnique();
        if (rabbitTemplate == null) {
            throw new QueueConfigurationException(
                "platform.queue.provider=RABBITMQ requires one Boot-managed RabbitTemplate");
        }
        return new RabbitMqMessagePublisher(rabbitTemplate, timeProvider);
    }
}
