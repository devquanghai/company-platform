package com.company.platform.queue;

import com.company.platform.core.time.TimeProvider;
import com.company.platform.queue.api.model.MessageEnvelope;
import com.company.platform.queue.api.model.MessageMetadata;
import com.company.platform.queue.application.port.out.PreparedMessage;
import com.company.platform.queue.autoconfigure.properties.BrokerProperties;
import com.company.platform.queue.autoconfigure.properties.DestinationProperties;
import com.company.platform.queue.domain.model.QueueProviderType;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

public final class QueueTestSupport {
    public static final Instant NOW = Instant.parse("2026-07-30T00:00:00Z");

    private QueueTestSupport() {
    }

    public static TimeProvider time() {
        return new TimeProvider() {
            @Override public Instant nowInstant() { return NOW; }
            @Override public OffsetDateTime now() {
                return OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC);
            }
            @Override public OffsetDateTime now(ZoneId zoneId) {
                return OffsetDateTime.ofInstant(NOW, zoneId);
            }
            @Override public ZoneId getDefaultZone() { return ZoneOffset.UTC; }
        };
    }

    public static MessageEnvelope<Map<String, String>> envelope() {
        return new MessageEnvelope<>(new MessageMetadata(
            "message-1", "event-1", "correlation-1", "cause-1",
            "0123456789abcdef0123456789abcdef", "0123456789abcdef",
            "test-app", "events", "TestEvent", 1, NOW, NOW,
            "application/json", Map.of("tenant-id", "tenant-a")),
            Map.of("value", "ok"));
    }

    public static PreparedMessage prepared(String broker) {
        return new PreparedMessage(
            broker, "events", "aggregate-1", null, null,
            envelope(), "{\"value\":\"ok\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    public static BrokerProperties kafkaBroker() {
        BrokerProperties broker = new BrokerProperties();
        broker.setProvider(QueueProviderType.KAFKA);
        broker.getKafka().setBootstrapServers(List.of("localhost:9092"));
        broker.getKafka().setSecurityProtocol("PLAINTEXT");
        broker.getKafka().getSsl().setEnabled(false);
        return broker;
    }

    public static DestinationProperties kafkaDestination() {
        DestinationProperties destination = new DestinationProperties();
        destination.setBroker("kafka-main");
        destination.getKafka().setTopic("events.v1");
        return destination;
    }

    public static BrokerProperties rabbitBroker() {
        BrokerProperties broker = new BrokerProperties();
        broker.setProvider(QueueProviderType.RABBITMQ);
        broker.getRabbit().setAddresses(List.of("localhost:5672"));
        broker.getRabbit().getSsl().setEnabled(false);
        return broker;
    }

    public static DestinationProperties rabbitDestination() {
        DestinationProperties destination = new DestinationProperties();
        destination.setBroker("rabbit-main");
        destination.getRabbit().setExchange("events.exchange");
        destination.getRabbit().setRoutingKey("events.created");
        return destination;
    }
}
