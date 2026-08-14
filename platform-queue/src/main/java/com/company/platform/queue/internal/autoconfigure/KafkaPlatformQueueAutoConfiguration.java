package com.company.platform.queue.internal.autoconfigure;

import com.company.platform.core.time.TimeProvider;
import com.company.platform.queue.api.publish.MessagePublisher;
import com.company.platform.queue.internal.publish.adapter.kafka.KafkaMessagePublisher;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.ObjectProvider;

@AutoConfiguration(
    after = PlatformQueueAutoConfiguration.class,
    afterName = "org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration")
@ConditionalOnClass(KafkaTemplate.class)
@ConditionalOnProperty(
    prefix = "platform.queue", name = "enabled",
    havingValue = "true", matchIfMissing = true)
@ConditionalOnProperty(
    prefix = "platform.queue", name = "provider", havingValue = "kafka",
    matchIfMissing = true)
public class KafkaPlatformQueueAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(MessagePublisher.class)
    MessagePublisher kafkaMessagePublisher(
        ObjectProvider<KafkaTemplate<Object, Object>> kafkaTemplates,
        TimeProvider timeProvider
    ) {
        KafkaTemplate<Object, Object> kafkaTemplate = kafkaTemplates.getIfUnique();
        if (kafkaTemplate == null) {
            throw new QueueConfigurationException(
                "platform.queue.provider=KAFKA requires one Boot-managed KafkaTemplate");
        }
        return new KafkaMessagePublisher(kafkaTemplate, timeProvider);
    }
}
