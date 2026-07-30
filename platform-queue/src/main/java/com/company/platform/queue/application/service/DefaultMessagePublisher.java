package com.company.platform.queue.application.service;

import com.company.platform.queue.api.model.MessageEnvelope;
import com.company.platform.queue.api.publish.MessagePublisher;
import com.company.platform.queue.api.publish.PublishRequest;
import com.company.platform.queue.api.publish.PublishResult;
import com.company.platform.queue.application.port.out.PreparedMessage;
import com.company.platform.queue.application.registry.MessagePublisherRegistry;
import com.company.platform.queue.application.registry.QueueBrokerRegistry;
import com.company.platform.queue.application.registry.QueueDestinationRegistry;
import com.company.platform.queue.autoconfigure.properties.PlatformQueueProperties;
import com.company.platform.queue.domain.model.PublishMode;
import com.company.platform.queue.envelope.codec.MessageEnvelopeFactory;
import com.company.platform.queue.reliability.outbox.TransactionalMessagePublisher;
import com.company.platform.queue.serialization.MessageSerializationContext;
import com.company.platform.queue.serialization.registry.MessageSerializerRegistry;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

public final class DefaultMessagePublisher implements MessagePublisher {
    private final PlatformQueueProperties properties;
    private final QueueBrokerRegistry brokers;
    private final QueueDestinationRegistry destinations;
    private final MessagePublisherRegistry publishers;
    private final MessageEnvelopeFactory envelopeFactory;
    private final MessageSerializerRegistry serializers;
    private final TransactionalMessagePublisher outboxPublisher;

    public DefaultMessagePublisher(
        PlatformQueueProperties properties,
        QueueBrokerRegistry brokers,
        QueueDestinationRegistry destinations,
        MessagePublisherRegistry publishers,
        MessageEnvelopeFactory envelopeFactory,
        MessageSerializerRegistry serializers,
        TransactionalMessagePublisher outboxPublisher
    ) {
        this.properties = properties;
        this.brokers = brokers;
        this.destinations = destinations;
        this.publishers = publishers;
        this.envelopeFactory = envelopeFactory;
        this.serializers = serializers;
        this.outboxPublisher = outboxPublisher;
    }

    @Override
    public <T> PublishResult publish(String destination, T payload) {
        return publish(PublishRequest.builder(payload).destination(destination).build());
    }

    @Override
    public <K, T> PublishResult publish(String destination, K key, T payload) {
        return publish(PublishRequest.builder(payload).destination(destination).key(key).build());
    }

    @Override
    public <T> PublishResult publish(PublishRequest<T> request) {
        Duration timeout = effectiveTimeout(request);
        try {
            return publishAsync(request).toCompletableFuture()
                .get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("publish interrupted", exception);
        } catch (Exception exception) {
            throw new IllegalStateException("publish failed or timed out", exception);
        }
    }

    @Override
    public <T> CompletionStage<PublishResult> publishAsync(PublishRequest<T> request) {
        Objects.requireNonNull(request, "request");
        var destination = destinations.requireEnabled(request.destination());
        var broker = brokers.require(destination.getBroker());
        PublishMode mode = request.mode() == null
            ? destination.getPublishMode() : request.mode();
        MessageEnvelope<T> envelope = envelopeFactory.create(request, destination);
        if (mode == PublishMode.OUTBOX) {
            if (outboxPublisher == null) {
                return CompletableFuture.failedFuture(
                    new IllegalStateException("outbox mode requires TransactionalMessagePublisher"));
            }
            return CompletableFuture.completedFuture(
                outboxPublisher.publish(request, envelope));
        }
        var format = request.serialization() == null
            ? destination.getSerialization().getFormat() : request.serialization();
        byte[] body = serializers.require(format).serialize(
            envelope,
            new MessageSerializationContext(
                envelope.metadata().eventType(), envelope.metadata().schemaVersion(),
                envelope.metadata().contentType(),
                properties.getDefaults().getMaxEnvelopeBytes()));
        PreparedMessage prepared = new PreparedMessage(
            destination.getBroker(), request.destination(), request.key(),
            request.partition(), request.routingKey(), envelope, body);
        return publishers.require(broker.getProvider())
            .publish(prepared, effectiveTimeout(request));
    }

    @Override
    public <T> List<PublishResult> publishBatch(List<PublishRequest<T>> requests) {
        return List.copyOf(requests).stream().map(this::publish).toList();
    }

    private Duration effectiveTimeout(PublishRequest<?> request) {
        var destination = destinations.requireEnabled(request.destination());
        return request.timeout() == null ? destination.getSendTimeout() : request.timeout();
    }
}
