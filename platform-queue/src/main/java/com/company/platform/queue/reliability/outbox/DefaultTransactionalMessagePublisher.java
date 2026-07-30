package com.company.platform.queue.reliability.outbox;

import com.company.platform.core.time.TimeProvider;
import com.company.platform.queue.api.model.MessageEnvelope;
import com.company.platform.queue.api.publish.PublishRequest;
import com.company.platform.queue.api.publish.PublishResult;
import com.company.platform.queue.application.registry.QueueBrokerRegistry;
import com.company.platform.queue.application.registry.QueueDestinationRegistry;
import com.company.platform.queue.autoconfigure.properties.PlatformQueueProperties;
import com.company.platform.queue.domain.result.PublishStatus;
import com.company.platform.queue.envelope.codec.MessageEnvelopeFactory;
import com.company.platform.queue.serialization.MessageSerializationContext;
import com.company.platform.queue.serialization.registry.MessageSerializerRegistry;

import java.time.Duration;

public final class DefaultTransactionalMessagePublisher
    implements TransactionalMessagePublisher {

    private final PlatformQueueProperties properties;
    private final OutboxMessageStore store;
    private final QueueDestinationRegistry destinations;
    private final QueueBrokerRegistry brokers;
    private final MessageEnvelopeFactory envelopeFactory;
    private final MessageSerializerRegistry serializers;
    private final TimeProvider time;

    public DefaultTransactionalMessagePublisher(
        PlatformQueueProperties properties,
        OutboxMessageStore store,
        QueueDestinationRegistry destinations,
        QueueBrokerRegistry brokers,
        MessageEnvelopeFactory envelopeFactory,
        MessageSerializerRegistry serializers,
        TimeProvider time
    ) {
        this.properties = properties;
        this.store = store;
        this.destinations = destinations;
        this.brokers = brokers;
        this.envelopeFactory = envelopeFactory;
        this.serializers = serializers;
        this.time = time;
    }

    @Override
    public <T> PublishResult publish(String destination, T payload) {
        PublishRequest<T> request = PublishRequest.builder(payload)
            .destination(destination)
            .mode(com.company.platform.queue.domain.model.PublishMode.OUTBOX)
            .build();
        return publish(request, envelopeFactory.create(
            request, destinations.requireEnabled(destination)));
    }

    @Override
    public PublishResult publish(
        PublishRequest<?> request, MessageEnvelope<?> envelope
    ) {
        var destination = destinations.requireEnabled(request.destination());
        var broker = brokers.require(destination.getBroker());
        var format = request.serialization() == null
            ? destination.getSerialization().getFormat() : request.serialization();
        byte[] bytes = serializers.require(format).serialize(
            envelope, new MessageSerializationContext(
                envelope.metadata().eventType(), envelope.metadata().schemaVersion(),
                envelope.metadata().contentType(),
                properties.getDefaults().getMaxEnvelopeBytes()));
        OutboxRecord record = new OutboxRecord(
            envelope.metadata().messageId(), null, null, request.destination(),
            request.key() == null ? null : String.valueOf(request.key()),
            envelope.metadata().messageId(), envelope.metadata().eventType(),
            envelope.metadata().schemaVersion(), bytes, envelope.metadata().headers(),
            time.nowInstant(), time.nowInstant(), null, OutboxStatus.PENDING, 0,
            null, null, 0, null);
        store.save(record);
        return new PublishResult(
            PublishStatus.OUTBOXED, broker.getProvider(), destination.getBroker(),
            request.destination(), null, envelope.metadata().messageId(), null,
            null, null, false, false, 0, Duration.ZERO, time.nowInstant(),
            envelope.metadata().traceId(), null);
    }
}
