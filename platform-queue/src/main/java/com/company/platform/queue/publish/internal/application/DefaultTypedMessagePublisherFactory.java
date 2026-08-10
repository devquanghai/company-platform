package com.company.platform.queue.publish.internal.application;

import com.company.platform.queue.api.publish.MessagePublisher;
import com.company.platform.queue.api.publish.PublishRequest;
import com.company.platform.queue.api.publish.PublishResult;
import com.company.platform.queue.api.publish.TypedMessagePublisher;
import com.company.platform.queue.api.publish.TypedMessagePublisherFactory;

import java.util.Objects;
import java.util.concurrent.CompletionStage;

public final class DefaultTypedMessagePublisherFactory implements TypedMessagePublisherFactory {
    private final MessagePublisher publisher;

    public DefaultTypedMessagePublisherFactory(MessagePublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public <T> TypedMessagePublisher<T> getPublisher(
        String destination, Class<T> payloadType
    ) {
        Objects.requireNonNull(payloadType, "payloadType");
        return new TypedMessagePublisher<>() {
            @Override public PublishResult publish(T payload) {
                return publisher.publish(destination, payloadType.cast(payload));
            }
            @Override public PublishResult publish(String key, T payload) {
                return publisher.publish(destination, key, payloadType.cast(payload));
            }
            @Override public CompletionStage<PublishResult> publishAsync(T payload) {
                return publisher.publishAsync(PublishRequest.builder(payloadType.cast(payload))
                    .destination(destination).build());
            }
        };
    }
}
