package com.company.platform.queue.publish.internal.adapter.rabbit;

import com.company.platform.core.time.TimeProvider;
import com.company.platform.queue.configuration.internal.registry.QueueDestinationRegistry;
import com.company.platform.queue.autoconfigure.properties.PlatformQueueProperties;
import com.company.platform.queue.domain.model.QueueProviderType;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

public final class RabbitPublisherFactory {
    private RabbitPublisherFactory() {
    }

    public static NamedRabbitPublisher create(
        PlatformQueueProperties properties,
        QueueDestinationRegistry destinations,
        TimeProvider time,
        RabbitTemplate template
    ) {
        Map<String, RabbitPublisherResources> resources = new LinkedHashMap<>();
        properties.getBrokers().forEach((name, broker) -> {
            if (broker.isEnabled() && broker.getProvider() == QueueProviderType.RABBITMQ) {
                resources.put(name, new RabbitPublisherResources(template));
            }
        });
        return new NamedRabbitPublisher(resources, destinations, time);
    }
}
