package com.company.platform.queue.internal.publish.adapter.rabbit;

import com.company.platform.core.time.TimeProvider;
import com.company.platform.queue.api.publish.MessagePublisher;
import com.company.platform.queue.api.publish.PublishRequest;
import com.company.platform.queue.api.publish.PublishResult;
import com.company.platform.queue.api.exception.QueuePublishException;
import com.company.platform.queue.api.model.QueueProviderType;
import com.company.platform.queue.api.model.PublishStatus;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.TimeUnit;

public final class RabbitMqMessagePublisher implements MessagePublisher {
    private static final Logger LOG = LoggerFactory.getLogger("PLATFORM_QUEUE");
    private static final int MAX_HEADERS = 64;
    private static final int MAX_HEADER_BYTES = 8 * 1024;
    private static final int MAX_TOTAL_HEADER_BYTES = 32 * 1024;
    private static final int MAX_MESSAGE_BYTES = 1024 * 1024;
    private static final int MAX_RABBIT_DESTINATION_LENGTH = 255;
    private static final Duration DEFAULT_CONFIRM_TIMEOUT = Duration.ofSeconds(10);
    private final RabbitTemplate template;
    private final TimeProvider timeProvider;

    public RabbitMqMessagePublisher(
        RabbitTemplate template,
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
        Objects.requireNonNull(key, "key");
        return publish(PublishRequest.builder(payload).destination(destination)
            .key(key).build());
    }

    @Override
    public <T> PublishResult publish(PublishRequest<T> request) {
        String stableMessageId = messageId(request);
        try {
            Duration timeout = publishTimeout(request);
            return send(request, stableMessageId).toCompletableFuture()
                .get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new QueuePublishException(
                "QUEUE.RABBITMQ.PUBLISH.UNKNOWN_OUTCOME",
                "RabbitMQ publish interrupted before confirmation", exception,
                stableMessageId, true);
        } catch (TimeoutException exception) {
            throw new QueuePublishException(
                "QUEUE.RABBITMQ.PUBLISH.UNKNOWN_OUTCOME",
                "RabbitMQ publish confirmation timed out", exception,
                stableMessageId, true);
        } catch (Exception exception) {
            Throwable cause = exception instanceof java.util.concurrent.ExecutionException
                ? exception.getCause() : exception;
            if (cause instanceof QueuePublishException queueFailure) {
                throw queueFailure;
            }
            throw failed(cause, stableMessageId);
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
        LOG.trace("queue_publish_started provider=RABBITMQ destination={}",
            request.destination());
        CorrelationData correlation = new CorrelationData(messageId);
        try {
            Message message = template.getMessageConverter().toMessage(
                request.payload(), new MessageProperties());
            applyHeaders(message, request, messageId);
            if (message.getBody().length > MAX_MESSAGE_BYTES) {
                throw new IllegalArgumentException(
                    "serialized RabbitMQ message exceeds 1 MiB");
            }
            if (request.key() == null) {
                template.send("", request.destination(), message, correlation);
            } else {
                template.send(request.destination(), String.valueOf(request.key()),
                    message, correlation);
            }
        } catch (RuntimeException failure) {
            LOG.debug("queue_publish_finished provider=RABBITMQ destination={} outcome=FAILED duration_ns={} error_type={}",
                request.destination(), System.nanoTime() - started,
                failure.getClass().getSimpleName());
            return CompletableFuture.failedFuture(failed(failure, messageId));
        }
        Duration timeout = publishTimeout(request);
        return correlation.getFuture()
            .orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)
            .handle((confirm, failure) -> {
                if (failure != null) {
                    Throwable cause = unwrap(failure);
                    LOG.debug("queue_publish_finished provider=RABBITMQ destination={} outcome=FAILED duration_ns={} error_type={}",
                        request.destination(), System.nanoTime() - started,
                        cause.getClass().getSimpleName());
                    QueuePublishException mapped = cause instanceof TimeoutException
                        ? new QueuePublishException(
                            "QUEUE.RABBITMQ.PUBLISH.UNKNOWN_OUTCOME",
                            "RabbitMQ publish confirmation timed out", cause,
                            messageId, true)
                        : failed(cause, messageId);
                    throw new CompletionException(mapped);
                }
                Instant completed = timeProvider.nowInstant();
                boolean returned = correlation.getReturned() != null;
                PublishStatus status = returned ? PublishStatus.RETURNED
                    : confirm.ack() ? PublishStatus.CONFIRMED : PublishStatus.REJECTED;
                PublishResult result = new PublishResult(
                    status, QueueProviderType.RABBITMQ,
                    request.destination(), messageId, completed,
                    returned ? "QUEUE.RABBITMQ.PUBLISH.RETURNED"
                        : confirm.ack() ? null : "QUEUE.RABBITMQ.PUBLISH.NACK");
                LOG.debug("queue_publish_finished provider=RABBITMQ destination={} outcome={} duration_ns={}",
                    request.destination(), status, System.nanoTime() - started);
                return result;
            });
    }

    private <T> void validateHeaders(PublishRequest<T> request, String messageId) {
        if (request.headers().size() > MAX_HEADERS) {
            throw new IllegalArgumentException("message header count exceeds 64");
        }
        int totalBytes = request.headers().entrySet().stream().mapToInt(entry ->
            entry.getKey().getBytes(StandardCharsets.UTF_8).length
                + Objects.requireNonNull(entry.getValue(), "header value")
                    .getBytes(StandardCharsets.UTF_8).length).sum();
        totalBytes += metadataBytes("x-message-id", messageId);
        totalBytes += metadataBytes("x-correlation-id", request.correlationId());
        totalBytes += metadataBytes("x-event-id", request.eventId());
        totalBytes += metadataBytes("x-causation-id", request.causationId());
        totalBytes += metadataBytes("x-event-type", request.eventType());
        if (request.schemaVersion() > 0) {
            totalBytes += metadataBytes(
                "x-schema-version", Integer.toString(request.schemaVersion()));
        }
        if (totalBytes > MAX_TOTAL_HEADER_BYTES) {
            throw new IllegalArgumentException("total message headers are too large");
        }
    }

    private <T> Message applyHeaders(
        Message message,
        PublishRequest<T> request,
        String messageId
    ) {
        validateHeaders(request, messageId);
        message.getMessageProperties().setMessageId(messageId);
        message.getMessageProperties().setCorrelationId(request.correlationId());
        setHeader(message, "x-event-id", request.eventId());
        setHeader(message, "x-causation-id", request.causationId());
        setHeader(message, "x-event-type", request.eventType());
        if (request.schemaVersion() > 0) {
            setHeader(message, "x-schema-version",
                Integer.toString(request.schemaVersion()));
        }
        request.headers().forEach((name, value) -> {
            rejectSensitiveHeader(name);
            if (Objects.requireNonNull(value, "header value")
                .getBytes(StandardCharsets.UTF_8).length > MAX_HEADER_BYTES) {
                throw new IllegalArgumentException("message header value is too large");
            }
            message.getMessageProperties().setHeader(name, value);
        });
        return message;
    }

    private int metadataBytes(String name, String value) {
        if (value == null) {
            return 0;
        }
        byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
        if (encoded.length > MAX_HEADER_BYTES) {
            throw new IllegalArgumentException("message metadata value is too large");
        }
        return name.getBytes(StandardCharsets.UTF_8).length + encoded.length;
    }

    private void setHeader(Message message, String name, String value) {
        if (value != null) {
            message.getMessageProperties().setHeader(name, value);
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
        validateBrokerName(request.destination(), "destination");
        if (request.key() != null) {
            validateBrokerName(String.valueOf(request.key()), "routing key");
        }
        publishTimeout(request);
    }

    private void validateBrokerName(String value, String kind) {
        if (value.length() > MAX_RABBIT_DESTINATION_LENGTH
            || !value.matches("[A-Za-z0-9._-]+")) {
            throw new IllegalArgumentException("invalid RabbitMQ " + kind);
        }
    }

    private Duration publishTimeout(PublishRequest<?> request) {
        Duration timeout = request.timeout() == null
            ? DEFAULT_CONFIRM_TIMEOUT : request.timeout();
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("publish timeout must be positive");
        }
        return timeout;
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

    private String messageId(PublishRequest<?> request) {
        return request.messageId() == null || request.messageId().isBlank()
            ? UUID.randomUUID().toString() : request.messageId();
    }

    private QueuePublishException failed(Throwable cause, String messageId) {
        return new QueuePublishException(
            "QUEUE.RABBITMQ.PUBLISH.FAILED", "RabbitMQ publish failed", cause,
            messageId, false);
    }
}
