package com.company.platform.queue.publish.internal.adapter.kafka;

import com.company.platform.core.time.TimeProvider;
import com.company.platform.queue.configuration.internal.registry.QueueDestinationRegistry;
import com.company.platform.queue.autoconfigure.properties.PlatformQueueProperties;
import com.company.platform.queue.api.kafka.KafkaPublishFailureHandler;
import com.company.platform.queue.domain.model.QueueProviderType;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;

public final class KafkaPublisherFactory {
    private KafkaPublisherFactory() {
    }

    public static NamedKafkaPublisher create(
        PlatformQueueProperties properties,
        QueueDestinationRegistry destinations,
        TimeProvider time,
        List<KafkaPublishFailureHandler> failureHandlers,
        KafkaTemplate<String, byte[]> template
    ) {
        Map<String, KafkaPublisherResources> resources = new LinkedHashMap<>();
        properties.getBrokers().forEach((name, broker) -> {
            if (broker.isEnabled() && broker.getProvider() == QueueProviderType.KAFKA) {
                resources.put(name, new KafkaPublisherResources(template));
            }
        });
        return new NamedKafkaPublisher(
            resources, destinations, time, failureHandlers);
    }
}
