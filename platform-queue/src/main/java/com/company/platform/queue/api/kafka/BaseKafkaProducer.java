package com.company.platform.queue.api.kafka;

import com.company.platform.queue.api.publish.MessagePublisher;
import com.company.platform.queue.api.publish.PublishResult;

import java.util.Objects;
import java.util.concurrent.CompletionStage;

public abstract class BaseKafkaProducer<T> {
    private final MessagePublisher publisher;
    private final String destination;
    private final Class<T> payloadType;

    protected BaseKafkaProducer(
        MessagePublisher publisher,
        KafkaDestinationResolver destinationResolver,
        String destination,
        Class<T> payloadType
    ) {
        this.publisher = Objects.requireNonNull(publisher, "publisher");
        this.destination = requireText(destination, "destination");
        this.payloadType = Objects.requireNonNull(payloadType, "payloadType");
        Objects.requireNonNull(destinationResolver, "destinationResolver")
            .requireKafkaDestination(this.destination);
    }

    public final PublishResult send(T payload) {
        return send(KafkaPublishMessage.builder(payload).build());
    }

    public final PublishResult send(String key, T payload) {
        return send(KafkaPublishMessage.builder(payload).key(key).build());
    }

    public final PublishResult send(KafkaPublishMessage<T> message) {
        KafkaPublishMessage<T> checked = checked(message);
        try {
            PublishResult result = publisher.publish(checked.toRequest(destination));
            notifyPublished(checked, result);
            return result;
        } catch (RuntimeException failure) {
            notifyFailure(checked, failure);
            throw failure;
        }
    }

    public final CompletionStage<PublishResult> sendAsync(T payload) {
        return sendAsync(KafkaPublishMessage.builder(payload).build());
    }

    public final CompletionStage<PublishResult> sendAsync(
        KafkaPublishMessage<T> message
    ) {
        KafkaPublishMessage<T> checked = checked(message);
        try {
            return publisher.publishAsync(checked.toRequest(destination))
                .whenComplete((result, failure) -> {
                    if (failure == null) {
                        notifyPublished(checked, result);
                    } else {
                        notifyFailure(checked, failure);
                    }
                });
        } catch (RuntimeException failure) {
            notifyFailure(checked, failure);
            throw failure;
        }
    }

    protected void onPublished(
        KafkaPublishMessage<T> message, PublishResult result
    ) {
    }

    protected void onPublishFailure(
        KafkaPublishMessage<T> message, Throwable failure
    ) {
    }

    private void notifyPublished(
        KafkaPublishMessage<T> message, PublishResult result
    ) {
        try {
            onPublished(message, result);
        } catch (RuntimeException ignored) {
            // Extension hooks are observational and cannot change publish outcome.
        }
    }

    private void notifyFailure(
        KafkaPublishMessage<T> message, Throwable failure
    ) {
        try {
            onPublishFailure(message, failure);
        } catch (RuntimeException ignored) {
            // Preserve the original publish failure.
        }
    }

    private KafkaPublishMessage<T> checked(KafkaPublishMessage<T> message) {
        Objects.requireNonNull(message, "message");
        payloadType.cast(message.payload());
        return message;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
