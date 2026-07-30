package com.company.platform.integration;

import com.company.platform.cache.api.lock.DistributedLockOperations;
import com.company.platform.cache.api.lock.LockOptions;
import com.company.platform.cache.api.operation.AtomicCacheOperations;
import com.company.platform.cache.api.operation.OptimisticCacheOperations;
import com.company.platform.cache.api.operation.PlatformCacheOperations;
import com.company.platform.cache.api.operation.TypedCacheFactory;
import com.company.platform.cache.application.port.out.CacheBackendRegistry;
import com.company.platform.cache.domain.result.OptimisticUpdateStatus;
import com.company.platform.cache.observability.event.CacheEventPublisher;
import com.company.platform.cache.observability.metrics.CacheMetricsRecorder;
import com.company.platform.queue.api.publish.MessagePublisher;
import com.company.platform.queue.api.publish.PublishRequest;
import com.company.platform.queue.domain.model.PublishMode;
import com.company.platform.queue.domain.result.PublishStatus;
import com.company.platform.queue.observability.event.QueueAuditEventPublisher;
import com.company.platform.queue.observability.metrics.QueueMetrics;
import com.company.platform.queue.reliability.outbox.OutboxStatus;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@ActiveProfiles("integration-e2e")
@Import(IntegrationE2EReliabilityConfiguration.class)
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PlatformCacheQueueE2ETest {
    @Container
    static final GenericContainer<?> REDIS =
        new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379);

    @Container
    static final KafkaContainer KAFKA =
        new KafkaContainer(DockerImageName.parse("apache/kafka-native:4.1.0"));

    @Container
    static final RabbitMQContainer RABBIT =
        new RabbitMQContainer(
            DockerImageName.parse("rabbitmq:4.1-management-alpine"));

    @Autowired PlatformCacheOperations cache;
    @Autowired AtomicCacheOperations atomic;
    @Autowired OptimisticCacheOperations optimistic;
    @Autowired TypedCacheFactory typedCaches;
    @Autowired DistributedLockOperations locks;
    @Autowired IntegrationCacheScenarioService annotationCache;
    @Autowired MessagePublisher messages;
    @Autowired IntegrationQueueListener listener;
    @Autowired MeterRegistry meters;
    @Autowired ApplicationContext context;
    @Autowired IntegrationE2EReliabilityConfiguration.FencedInMemoryOutboxStore outbox;

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry registry) {
        registry.add(
            "platform.cache.stores.redis.redis.standalone.host", REDIS::getHost);
        registry.add(
            "platform.cache.stores.redis.redis.standalone.port",
            () -> REDIS.getMappedPort(6379));
        registry.add(
            "platform.queue.brokers.kafka-main.kafka.bootstrap-servers[0]",
            KAFKA::getBootstrapServers);
        registry.add(
            "platform.queue.brokers.rabbit-main.rabbit.addresses[0]",
            () -> RABBIT.getHost() + ":" + RABBIT.getAmqpPort());
        registry.add(
            "platform.queue.brokers.rabbit-main.rabbit.username",
            RABBIT::getAdminUsername);
        registry.add(
            "platform.queue.brokers.rabbit-main.rabbit.password",
            RABBIT::getAdminPassword);
    }

    @Test
    void enablesAllCacheAndQueueInfrastructure() {
        assertThat(context.getBean(CacheBackendRegistry.class).snapshot())
            .containsKeys(
                "integration-local", "integration-redis", "integration-multi",
                "integration-fallback", "integration-coordination",
                "integration-noop");
        assertThat(context.getBean(CacheMetricsRecorder.class)).isNotNull();
        assertThat(context.getBean(CacheEventPublisher.class)).isNotNull();
        assertThat(context.getBean(QueueMetrics.class)).isNotNull();
        assertThat(context.getBean(QueueAuditEventPublisher.class)).isNotNull();
        assertThat(context.getBean(
            "platformCacheHealthIndicator", HealthIndicator.class)
            .health().getStatus().getCode()).isEqualTo("UP");
        assertThat(context.getBean(
            "platformQueueHealthIndicator", HealthIndicator.class)
            .health().getDetails()).containsEntry("enabledBrokers", 2L);
        assertThat(context.containsBean(
            "platformCacheRedisConnectionFactory__redis")).isTrue();
    }

    @Test
    void exercisesLocalRedisMultiLevelNoopAndSpringCache() {
        cache.put("integration-local", "local-key", "local-value");
        cache.put("integration-redis", "redis-key", "redis-value");
        cache.put("integration-multi", "multi-key", "multi-value");
        cache.put("integration-fallback", "fallback-key", "fallback-value");
        cache.put("integration-noop", "noop-key", "ignored");

        assertThat(cache.get(
            "integration-local", "local-key", String.class))
            .contains("local-value");
        assertThat(cache.get(
            "integration-redis", "redis-key", String.class))
            .contains("redis-value");
        assertThat(cache.get(
            "integration-multi", "multi-key", String.class))
            .contains("multi-value");
        assertThat(cache.get(
            "integration-fallback", "fallback-key", String.class))
            .contains("fallback-value");
        assertThat(cache.get(
            "integration-noop", "noop-key", String.class)).isEmpty();

        AtomicInteger negativeLoads = new AtomicInteger();
        assertThat(cache.getOrLoad(
            "integration-local", "negative-key", String.class, () -> {
                negativeLoads.incrementAndGet();
                return null;
            })).isNull();
        assertThat(cache.getOrLoad(
            "integration-local", "negative-key", String.class, () -> {
                negativeLoads.incrementAndGet();
                return "unexpected";
            })).isNull();
        assertThat(negativeLoads).hasValue(1);

        var typed = typedCaches.getCache(
            "integration-redis", String.class, String.class);
        typed.put("typed-key", "typed-value");
        assertThat(typed.get("typed-key")).contains("typed-value");

        int before = annotationCache.loadCount();
        String first = annotationCache.cached("spring-cache");
        String second = annotationCache.cached("spring-cache");
        assertThat(second).isEqualTo(first);
        assertThat(annotationCache.loadCount()).isEqualTo(before + 1);
        annotationCache.evict("spring-cache");
        assertThat(annotationCache.cached("spring-cache")).isNotEqualTo(first);

        assertThat(meters.find("platform.cache.operations").counters())
            .isNotEmpty();
    }

    @Test
    void exercisesRedisAtomicOptimisticAndDistributedLock() {
        assertThat(atomic.increment(
            "integration-coordination", "counter", 2)).isEqualTo(2);
        assertThat(atomic.decrement(
            "integration-coordination", "counter", 1)).isEqualTo(1);

        cache.put("integration-coordination", "cas", "old");
        assertThat(atomic.compareAndSet(
            "integration-coordination", "cas", "old", "new")).isTrue();
        assertThat(cache.get(
            "integration-coordination", "cas", String.class)).contains("new");

        cache.put("integration-coordination", "versioned", "v1");
        var current = optimistic.getVersioned(
            "integration-coordination", "versioned", String.class);
        var updated = optimistic.updateIfVersion(
            "integration-coordination", "versioned",
            current.getVersion(), "v2");
        assertThat(updated.getStatus()).isEqualTo(OptimisticUpdateStatus.UPDATED);
        assertThat(optimistic.updateIfVersion(
            "integration-coordination", "versioned",
            current.getVersion(), "v3").getStatus())
            .isEqualTo(OptimisticUpdateStatus.VERSION_CONFLICT);

        AtomicInteger executions = new AtomicInteger();
        String result = locks.executeWithLock(
            "cache-e2e",
            LockOptions.builder()
                .waitTime(Duration.ofSeconds(2))
                .leaseTime(Duration.ofSeconds(5))
                .fencingEnabled(true)
                .build(),
            () -> "locked-" + executions.incrementAndGet());
        assertThat(result).isEqualTo("locked-1");
        assertThat(executions).hasValue(1);
    }

    @Test
    void publishesKafkaRabbitAndTransactionalOutboxEndToEnd()
        throws Exception {
        IntegrationQueueEvent kafkaEvent =
            new IntegrationQueueEvent(UUID.randomUUID().toString(), "kafka");
        var kafkaResult = messages.publish(request(
            "kafka-events", kafkaEvent, PublishMode.DIRECT));
        assertThat(kafkaResult.status()).isEqualTo(PublishStatus.CONFIRMED);
        var kafkaReceived = listener.await(
            received -> received.provider().equals("kafka")
                && received.event().id().equals(kafkaEvent.id()),
            Duration.ofSeconds(20));
        assertThat(kafkaReceived).isNotNull();
        assertThat(kafkaReceived.context().messageId()).isNotBlank();
        assertThat(kafkaReceived.context().consumerGroup())
            .isEqualTo("platform-integration-e2e");
        assertThat(messages.publish(request(
            "kafka-events", kafkaEvent, PublishMode.DIRECT,
            kafkaResult.messageId())).status()).isEqualTo(PublishStatus.CONFIRMED);
        assertThat(listener.await(
            received -> received.provider().equals("kafka")
                && received.event().id().equals(kafkaEvent.id()),
            Duration.ofSeconds(2))).isNull();

        IntegrationQueueEvent rabbitEvent =
            new IntegrationQueueEvent(UUID.randomUUID().toString(), "rabbit");
        var rabbitResult = messages.publish(request(
            "rabbit-events", rabbitEvent, PublishMode.DIRECT));
        assertThat(rabbitResult.status()).isEqualTo(PublishStatus.CONFIRMED);
        var rabbitReceived = listener.await(
            received -> received.provider().equals("rabbit")
                && received.event().id().equals(rabbitEvent.id()),
            Duration.ofSeconds(20));
        assertThat(rabbitReceived).isNotNull();
        assertThat(rabbitReceived.context().exchange())
            .isEqualTo("platform.integration.events");

        IntegrationQueueEvent outboxEvent =
            new IntegrationQueueEvent(UUID.randomUUID().toString(), "outbox");
        String outboxMessageId = UUID.randomUUID().toString();
        var outboxResult = messages.publish(PublishRequest.builder(outboxEvent)
            .destination("kafka-events")
            .key(outboxEvent.id())
            .messageId(outboxMessageId)
            .correlationId(outboxMessageId)
            .eventType(IntegrationQueueEvent.class.getSimpleName())
            .schemaVersion(1)
            .mode(PublishMode.OUTBOX)
            .build());
        assertThat(outboxResult.status()).isEqualTo(PublishStatus.OUTBOXED);
        assertThat(listener.await(
            received -> received.provider().equals("kafka")
                && received.event().id().equals(outboxEvent.id()),
            Duration.ofSeconds(20))).isNotNull();
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
            assertThat(outbox.get(outboxMessageId).status())
                .isEqualTo(OutboxStatus.PUBLISHED));
    }

    private PublishRequest<IntegrationQueueEvent> request(
        String destination,
        IntegrationQueueEvent event,
        PublishMode mode
    ) {
        return request(
            destination, event, mode, UUID.randomUUID().toString());
    }

    private PublishRequest<IntegrationQueueEvent> request(
        String destination,
        IntegrationQueueEvent event,
        PublishMode mode,
        String messageId
    ) {
        return PublishRequest.builder(event)
            .destination(destination)
            .key(event.id())
            .messageId(messageId)
            .correlationId(messageId)
            .eventType(IntegrationQueueEvent.class.getSimpleName())
            .schemaVersion(1)
            .header("tenant-id", "integration")
            .mode(mode)
            .build();
    }
}
