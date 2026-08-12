package com.company.platform.queue.publish.internal.adapter.kafka;

import com.company.platform.core.time.TimeProvider;
import com.company.platform.queue.api.kafka.KafkaQueueOperations;
import com.company.platform.queue.api.kafka.KafkaPublishFailure;
import com.company.platform.queue.api.kafka.KafkaPublishFailureHandler;
import com.company.platform.queue.api.publish.PublishResult;
import com.company.platform.queue.publish.internal.port.out.PreparedMessage;
import com.company.platform.queue.publish.internal.port.out.ProviderMessagePublisher;
import com.company.platform.queue.configuration.internal.registry.QueueDestinationRegistry;
import com.company.platform.queue.domain.model.QueueProviderType;
import com.company.platform.queue.domain.result.PublishStatus;
import com.company.platform.queue.envelope.header.PlatformMessageHeaders;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.kafka.core.KafkaTemplate;
import com.company.platform.queue.consume.internal.port.out.KafkaDeadLetterPublisher;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class NamedKafkaPublisher
    implements ProviderMessagePublisher, KafkaQueueOperations,
    KafkaDeadLetterPublisher, DisposableBean {

    private final Map<String, KafkaPublisherResources> resources;
    private final QueueDestinationRegistry destinations;
    private final TimeProvider time;
    private final List<KafkaPublishFailureHandler> failureHandlers;

    public NamedKafkaPublisher(
        Map<String, KafkaPublisherResources> resources,
        QueueDestinationRegistry destinations,
        TimeProvider time,
        List<KafkaPublishFailureHandler> failureHandlers
    ) {
        this.resources = Map.copyOf(resources);
        this.destinations = destinations;
        this.time = time;
        this.failureHandlers = List.copyOf(failureHandlers);
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
        String topic = null;
        try {
            KafkaTemplate<String, byte[]> template = require(message.broker()).template();
            var destination = destinations.requireEnabled(message.destination());
            topic = destination.getKafka().getTopic();
            String key = message.key() == null ? null : String.valueOf(message.key());
            if (destination.getKafka().isKeyRequired()
                && (key == null || key.isBlank())) {
                throw new IllegalArgumentException(
                    "Kafka destination requires a stable key");
            }
            if (message.partition() != null
                && !destination.getKafka().isPartitionOverrideAllowed()) {
                throw new IllegalArgumentException(
                    "Kafka partition override is disabled");
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
            String resolvedTopic = topic;
            return template.send(record)
                .orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)
                .thenApply(result -> new PublishResult(
                PublishStatus.CONFIRMED, provider(), message.broker(),
                message.destination(), result.getRecordMetadata().topic(),
                message.envelope().metadata().messageId(),
                result.getRecordMetadata().partition(),
                result.getRecordMetadata().offset(), null, true, false, 1,
                Duration.ofNanos(System.nanoTime() - started), time.nowInstant(),
                message.envelope().metadata().traceId(), null))
                .whenComplete((result, failure) -> {
                    if (failure != null) {
                        notifyFailure(message, resolvedTopic, failure);
                    }
                });
        } catch (RuntimeException failure) {
            notifyFailure(message, topic, failure);
            return CompletableFuture.failedFuture(failure);
        }
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
    public void publishDeadLetter(
        String brokerName,
        String topic,
        int partition,
        String key,
        byte[] body,
        org.apache.kafka.common.header.Headers headers,
        Duration timeout
    ) {
        ProducerRecord<String, byte[]> record = new ProducerRecord<>(
            topic, partition, key, body, headers);
        try {
            require(brokerName).template().send(record).get(
                timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Kafka dead-letter publish interrupted", exception);
        } catch (java.util.concurrent.ExecutionException
            | java.util.concurrent.TimeoutException exception) {
            throw new IllegalStateException("Kafka dead-letter publish failed", exception);
        }
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

    private void notifyFailure(
        PreparedMessage message, String topic, Throwable failure
    ) {
        Throwable cause = unwrap(failure);
        PublishStatus status = timeout(cause)
            ? PublishStatus.UNKNOWN_OUTCOME : PublishStatus.FAILED;
        String failureCode = status == PublishStatus.UNKNOWN_OUTCOME
            ? "QUEUE.KAFKA_PUBLISH_OUTCOME_UNKNOWN" : "QUEUE.KAFKA_PUBLISH_FAILED";
        KafkaPublishFailure event = new KafkaPublishFailure(
            status, message.broker(), message.destination(), topic,
            message.envelope().metadata().messageId(),
            message.envelope().metadata().correlationId(),
            message.envelope().metadata().traceId(), failureCode,
            cause.getClass().getSimpleName());
        log.warn("Kafka publish failed broker={} destination={} topic={} messageId={} status={} failureCode={} failureType={}",
            message.broker(), message.destination(), topic,
            message.envelope().metadata().messageId(), status, failureCode,
            cause.getClass().getSimpleName());
        failureHandlers.forEach(handler -> {
            try {
                handler.onFailure(event);
            } catch (RuntimeException ignored) {
                // Failure hooks are observational and must not replace the publish failure.
            }
        });
    }

    private Throwable unwrap(Throwable failure) {
        Throwable current = failure;
        while ((current instanceof CompletionException
            || current instanceof java.util.concurrent.ExecutionException)
            && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private boolean timeout(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof TimeoutException
                || current instanceof org.apache.kafka.common.errors.TimeoutException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
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
