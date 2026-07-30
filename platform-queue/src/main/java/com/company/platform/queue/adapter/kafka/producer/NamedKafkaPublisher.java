package com.company.platform.queue.adapter.kafka.producer;

import com.company.platform.core.time.TimeProvider;
import com.company.platform.queue.api.kafka.KafkaQueueOperations;
import com.company.platform.queue.api.publish.PublishResult;
import com.company.platform.queue.application.port.out.PreparedMessage;
import com.company.platform.queue.application.port.out.ProviderMessagePublisher;
import com.company.platform.queue.application.registry.QueueDestinationRegistry;
import com.company.platform.queue.domain.model.QueueProviderType;
import com.company.platform.queue.domain.result.PublishStatus;
import com.company.platform.queue.envelope.header.PlatformMessageHeaders;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.kafka.core.KafkaTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public final class NamedKafkaPublisher
    implements ProviderMessagePublisher, KafkaQueueOperations, DisposableBean {

    private final Map<String, KafkaPublisherResources> resources;
    private final QueueDestinationRegistry destinations;
    private final TimeProvider time;

    public NamedKafkaPublisher(
        Map<String, KafkaPublisherResources> resources,
        QueueDestinationRegistry destinations,
        TimeProvider time
    ) {
        this.resources = Map.copyOf(resources);
        this.destinations = destinations;
        this.time = time;
    }

    @Override
    public QueueProviderType provider() {
        return QueueProviderType.KAFKA;
    }

    @Override
    public CompletionStage<PublishResult> publish(
        PreparedMessage message, Duration timeout
    ) {
        long started = System.nanoTime();
        KafkaTemplate<String, byte[]> template = require(message.broker()).template();
        var destination = destinations.requireEnabled(message.destination());
        String topic = destination.getKafka().getTopic();
        String key = message.key() == null ? null : String.valueOf(message.key());
        if (destination.getKafka().isRequireKey() && (key == null || key.isBlank())) {
            return java.util.concurrent.CompletableFuture.failedFuture(
                new IllegalArgumentException("Kafka destination requires a stable key"));
        }
        if (message.partition() != null
            && !destination.getKafka().isAllowPartitionOverride()) {
            return java.util.concurrent.CompletableFuture.failedFuture(
                new IllegalArgumentException("Kafka partition override is disabled"));
        }
        RecordHeaders headers = new RecordHeaders();
        add(headers, PlatformMessageHeaders.MESSAGE_ID,
            message.envelope().metadata().messageId());
        add(headers, PlatformMessageHeaders.EVENT_TYPE,
            message.envelope().metadata().eventType());
        add(headers, PlatformMessageHeaders.SCHEMA_VERSION,
            Integer.toString(message.envelope().metadata().schemaVersion()));
        add(headers, PlatformMessageHeaders.CORRELATION_ID,
            message.envelope().metadata().correlationId());
        add(headers, PlatformMessageHeaders.CAUSATION_ID,
            message.envelope().metadata().causationId());
        add(headers, PlatformMessageHeaders.TRACEPARENT,
            traceparent(message.envelope()));
        add(headers, PlatformMessageHeaders.SOURCE_APPLICATION,
            message.envelope().metadata().sourceApplication());
        add(headers, PlatformMessageHeaders.CONTENT_TYPE,
            message.envelope().metadata().contentType());
        message.envelope().metadata().headers().forEach(
            (name, value) -> add(headers, name, value));
        ProducerRecord<String, byte[]> record = new ProducerRecord<>(
            topic, message.partition(), key, message.body(), headers);
        return template.send(record)
            .orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)
            .thenApply(result -> new PublishResult(
                PublishStatus.CONFIRMED, provider(), message.broker(),
                message.destination(), result.getRecordMetadata().topic(),
                message.envelope().metadata().messageId(),
                result.getRecordMetadata().partition(),
                result.getRecordMetadata().offset(), null, true, false, 1,
                Duration.ofNanos(System.nanoTime() - started), time.nowInstant(),
                message.envelope().metadata().traceId(), null));
    }

    @Override
    public <T> T executeInTransaction(String brokerName, Supplier<T> action) {
        Objects.requireNonNull(action, "action");
        return require(brokerName).template().executeInTransaction(operations -> action.get());
    }

    @Override
    public void flush(String brokerName) {
        require(brokerName).template().flush();
    }

    @Override
    public int partitionCount(String brokerName, String topic, Duration timeout) {
        return require(brokerName).template().partitionsFor(topic).size();
    }

    @Override
    public void destroy() {
        resources.values().forEach(value -> value.producerFactory().destroy());
    }

    private KafkaPublisherResources require(String broker) {
        KafkaPublisherResources value = resources.get(broker);
        if (value == null) {
            throw new IllegalArgumentException("unknown Kafka broker");
        }
        return value;
    }

    private void add(RecordHeaders headers, String name, String value) {
        if (value != null) {
            headers.add(name, value.getBytes(StandardCharsets.UTF_8));
        }
    }

    private String traceparent(com.company.platform.queue.api.model.MessageEnvelope<?> envelope) {
        String traceId = envelope.metadata().traceId();
        String spanId = envelope.metadata().spanId();
        return traceId == null || spanId == null
            ? null : "00-" + traceId + "-" + spanId + "-01";
    }
}
