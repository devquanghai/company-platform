package com.company.platform.queue.publish.internal.adapter.kafka;

import com.company.platform.core.time.TimeProvider;
import com.company.platform.queue.configuration.internal.registry.QueueDestinationRegistry;
import com.company.platform.queue.autoconfigure.properties.PlatformQueueProperties;
import com.company.platform.queue.api.kafka.KafkaPublishFailureHandler;
import com.company.platform.queue.domain.model.QueueProviderType;
import com.company.platform.queue.configuration.internal.adapter.kafka.KafkaSecurityConfiguration;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;

public final class KafkaPublisherFactory {
    private static final int SAFE_RETRIES = 10;
    private static final int SAFE_MAX_IN_FLIGHT = 5;

    private KafkaPublisherFactory() {
    }

    public static NamedKafkaPublisher create(
        PlatformQueueProperties properties,
        QueueDestinationRegistry destinations,
        TimeProvider time,
        List<KafkaPublishFailureHandler> failureHandlers
    ) {
        Map<String, KafkaPublisherResources> resources = new LinkedHashMap<>();
        properties.getBrokers().forEach((name, broker) -> {
            if (broker.isEnabled() && broker.getProvider() == QueueProviderType.KAFKA) {
                var kafka = broker.getKafka();
                Map<String, Object> config = new LinkedHashMap<>();
                config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
                config.put(ProducerConfig.CLIENT_ID_CONFIG, kafka.getClientIdPrefix() + "-" + name);
                config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
                config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
                config.put(ProducerConfig.ACKS_CONFIG, "all");
                config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
                config.put(ProducerConfig.RETRIES_CONFIG, SAFE_RETRIES);
                config.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION,
                    SAFE_MAX_IN_FLIGHT);
                config.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG,
                    Math.toIntExact(kafka.getRequestTimeout().toMillis()));
                config.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG,
                    Math.toIntExact(kafka.getDeliveryTimeout().toMillis()));
                config.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, kafka.getMaxBlock().toMillis());
                KafkaSecurityConfiguration.apply(config, kafka);
                var factory = new DefaultKafkaProducerFactory<String, byte[]>(config);
                if (kafka.isTransactionsEnabled()) {
                    factory.setTransactionIdPrefix(
                        kafka.getTransactionalIdPrefix()
                            + kafka.getTransactionalInstanceId() + "-" + name + "-");
                }
                KafkaTemplate<String, byte[]> template = new KafkaTemplate<>(factory);
                template.setAllowNonTransactional(true);
                template.setObservationEnabled(
                    properties.getObservability().isTracingEnabled());
                resources.put(name, new KafkaPublisherResources(factory, template));
            }
        });
        return new NamedKafkaPublisher(
            resources, destinations, time, failureHandlers);
    }
}
