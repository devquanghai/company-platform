package com.company.platform.queue.internal.publish.adapter.kafka;

import com.company.platform.core.time.TimeProvider;
import com.company.platform.queue.api.publish.MessagePublisher;
import com.company.platform.queue.api.publish.PublishRequest;
import com.company.platform.queue.api.publish.PublishResult;
import com.company.platform.queue.api.exception.QueuePublishException;
import com.company.platform.queue.api.model.QueueProviderType;
import com.company.platform.queue.api.model.PublishStatus;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.core.KafkaTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class KafkaMessagePublisher implements MessagePublisher {
    private static final Logger LOG = LoggerFactory.getLogger("PLATFORM_QUEUE");
    private static final int MAX_HEADERS = 64;
    private static final int MAX_HEADER_BYTES = 8 * 1024;
    private static final int MAX_TOTAL_HEADER_BYTES = 32 * 1024;
    private static final Duration DEFAULT_PUBLISH_TIMEOUT = Duration.ofSeconds(10);
    private static final int MAX_KAFKA_TOPIC_LENGTH = 249;
    private static final int MAX_DIRECT_PAYLOAD_BYTES = 1024 * 1024;
    private final KafkaTemplate<Object, Object> template;
    private final TimeProvider timeProvider;

    public KafkaMessagePublisher(
        KafkaTemplate<Object, Object> template,
        TimeProvider timeProvider
    ) {
        this.template = Objects.requireNonNull(template, "template");
        this.timeProvider = Objects.requireNonNull(timeProvider, "timeProvider");
    }

    @Override
    public <T> PublishResult publish(String destination, T payload) {
        return publish(PublishRequest.builder(payload).destination(destination).build());
    }

    @Override
    public <K, T> PublishResult publish(String destination, K key, T payload) {
        return publish(PublishRequest.builder(payload).destination(destination)
            .key(key).build());
    }

    @Override
    public <T> PublishResult publish(PublishRequest<T> request) {
        String stableMessageId = messageId(request);
        try {
            var future = send(request, stableMessageId).toCompletableFuture();
            Duration timeout = publishTimeout(request);
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw unknown("Kafka publish interrupted", exception, stableMessageId);
        } catch (java.util.concurrent.TimeoutException exception) {
            throw unknown("Kafka publish confirmation timed out", exception,
                stableMessageId);
        } catch (CompletionException exception) {
            throw classifiedFailure(exception.getCause(), stableMessageId);
        } catch (Exception exception) {
            throw classifiedFailure(exception, stableMessageId);
        }
    }

    @Override
    public <T> CompletionStage<PublishResult> publishAsync(PublishRequest<T> request) {
        return send(request, messageId(request));
    }

    private <T> CompletionStage<PublishResult> send(
        PublishRequest<T> request,
        String messageId
    ) {
        validate(request);
        long started = System.nanoTime();
        LOG.trace("queue_publish_started provider=KAFKA destination={}",
            request.destination());
        ProducerRecord<Object, Object> record = new ProducerRecord<>(
            request.destination(), null, request.key(), request.payload(),
            headers(request, messageId));
        Duration timeout = publishTimeout(request);
        return template.send(record)
            .orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)
            .handle((result, failure) -> {
            if (failure != null) {
                LOG.debug("queue_publish_finished provider=KAFKA destination={} outcome=FAILED duration_ns={} error_type={}",
                    request.destination(), System.nanoTime() - started,
                    unwrap(failure).getClass().getSimpleName());
                throw new CompletionException(classifiedFailure(failure, messageId));
            }
            PublishResult published = result(request, messageId);
            LOG.debug("queue_publish_finished provider=KAFKA destination={} outcome={} duration_ns={}",
                request.destination(), published.status(), System.nanoTime() - started);
            return published;
        });
    }

    private <T> PublishResult result(
        PublishRequest<T> request,
        String messageId
    ) {
        return new PublishResult(
            PublishStatus.PUBLISHED, QueueProviderType.KAFKA,
            request.destination(), messageId, timeProvider.nowInstant(), null);
    }

    private <T> Iterable<Header> headers(
        PublishRequest<T> request,
        String messageId
    ) {
        if (request.headers().size() > MAX_HEADERS) {
            throw new IllegalArgumentException("message header count exceeds 64");
        }
        List<Header> headers = new ArrayList<>();
        addHeader(headers, "x-message-id", messageId);
        addHeader(headers, "x-correlation-id", request.correlationId());
        addHeader(headers, "x-event-id", request.eventId());
        addHeader(headers, "x-causation-id", request.causationId());
        addHeader(headers, "x-event-type", request.eventType());
        if (request.schemaVersion() > 0) {
            addHeader(headers, "x-schema-version",
                Integer.toString(request.schemaVersion()));
        }
        request.headers().forEach((name, value) -> {
            rejectSensitiveHeader(name);
            addHeader(headers, name, value);
        });
        int totalBytes = headers.stream().mapToInt(header ->
            header.key().getBytes(StandardCharsets.UTF_8).length
                + header.value().length).sum();
        if (totalBytes > MAX_TOTAL_HEADER_BYTES) {
            throw new IllegalArgumentException("total message headers are too large");
        }
        return headers;
    }

    private void addHeader(List<Header> headers, String name, String value) {
        if (value != null) {
            byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
            if (encoded.length > MAX_HEADER_BYTES) {
                throw new IllegalArgumentException("message header value is too large");
            }
            headers.add(new RecordHeader(
                name, encoded));
        }
    }

    private void rejectSensitiveHeader(String name) {
        String normalized = Objects.requireNonNull(name, "header name")
            .toLowerCase(Locale.ROOT);
        if (normalized.isBlank() || normalized.length() > 128
            || normalized.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("invalid message header name");
        }
        if (normalized.contains("authorization") || normalized.contains("cookie")
            || normalized.contains("password") || normalized.contains("secret")
            || normalized.contains("token") || normalized.startsWith("__")
            || normalized.startsWith("x-message-")
            || normalized.equals("x-correlation-id")
            || normalized.equals("x-event-id")
            || normalized.equals("x-causation-id")
            || normalized.equals("x-event-type")
            || normalized.equals("x-schema-version")) {
            throw new IllegalArgumentException("sensitive or reserved header is forbidden");
        }
    }

    private <T> void validate(PublishRequest<T> request) {
        Objects.requireNonNull(request, "request");
        validateDestination(request.destination(), MAX_KAFKA_TOPIC_LENGTH, "topic");
        validateDirectPayload(request.payload());
        publishTimeout(request);
    }

    private void validateDirectPayload(Object payload) {
        int size = payload instanceof byte[] bytes ? bytes.length
            : payload instanceof CharSequence text
                ? text.toString().getBytes(StandardCharsets.UTF_8).length : -1;
        if (size > MAX_DIRECT_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("Kafka payload exceeds 1 MiB");
        }
    }

    private void validateDestination(String destination, int maxLength, String kind) {
        if (destination.length() > maxLength
            || !destination.matches("[A-Za-z0-9._-]+")
            || destination.equals(".") || destination.equals("..")) {
            throw new IllegalArgumentException("invalid Kafka " + kind);
        }
    }

    private Duration publishTimeout(PublishRequest<?> request) {
        Duration timeout = request.timeout() == null
            ? DEFAULT_PUBLISH_TIMEOUT : request.timeout();
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("publish timeout must be positive");
        }
        return timeout;
    }

    private String messageId(PublishRequest<?> request) {
        return request.messageId() == null || request.messageId().isBlank()
            ? UUID.randomUUID().toString() : request.messageId();
    }

    private QueuePublishException classifiedFailure(
        Throwable cause,
        String messageId
    ) {
        Throwable current = cause;
        while (current != null) {
            if (current instanceof TimeoutException
                || current instanceof org.apache.kafka.common.errors.TimeoutException) {
                return unknown("Kafka publish outcome is unknown", cause, messageId);
            }
            current = current.getCause();
        }
        return new QueuePublishException(
            "QUEUE.KAFKA.PUBLISH.FAILED", "Kafka publish failed", cause,
            messageId, false);
    }

    private QueuePublishException unknown(
        String message,
        Throwable cause,
        String messageId
    ) {
        return new QueuePublishException(
            "QUEUE.KAFKA.PUBLISH.UNKNOWN_OUTCOME", message, cause,
            messageId, true);
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
}
