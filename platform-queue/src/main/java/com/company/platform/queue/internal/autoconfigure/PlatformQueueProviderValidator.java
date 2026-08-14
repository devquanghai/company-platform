package com.company.platform.queue.internal.autoconfigure;

import com.company.platform.queue.api.model.QueueProviderType;
import com.company.platform.queue.internal.configuration.properties.PlatformQueueProperties;
import com.company.platform.queue.api.publish.MessagePublisher;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;
import org.springframework.util.ClassUtils;

import java.util.Map;
import java.util.Set;

final class PlatformQueueProviderValidator {
    private static final Set<String> SUPPORTED_KEYS = Set.of("enabled", "provider");
    private final PlatformQueueProperties properties;
    private final Environment environment;
    private final ObjectProvider<MessagePublisher> publishers;

    PlatformQueueProviderValidator(
        PlatformQueueProperties properties,
        Environment environment,
        ObjectProvider<MessagePublisher> publishers
    ) {
        this.properties = properties;
        this.environment = environment;
        this.publishers = publishers;
    }

    public void validate() {
        rejectLegacyProperties();
        if (publishers.stream().findAny().isPresent()) {
            return;
        }
        String requiredClass = properties.getProvider() == QueueProviderType.KAFKA
            ? "org.springframework.kafka.core.KafkaTemplate"
            : "org.springframework.amqp.rabbit.core.RabbitTemplate";
        if (!ClassUtils.isPresent(requiredClass, getClass().getClassLoader())) {
            throw new QueueConfigurationException(
                "platform.queue.provider=" + properties.getProvider()
                    + " is configured but the required Spring broker integration "
                    + "is not available on the classpath");
        }
    }

    private void rejectLegacyProperties() {
        Map<String, Object> values = Binder.get(environment)
            .bind("platform.queue", Bindable.mapOf(String.class, Object.class))
            .orElse(Map.of());
        values.keySet().stream()
            .filter(key -> !SUPPORTED_KEYS.contains(key))
            .findFirst()
            .ifPresent(key -> {
                throw new QueueConfigurationException(
                    "Unsupported legacy property platform.queue." + key
                        + "; use spring.kafka.* or spring.rabbitmq.*");
            });
    }
}
