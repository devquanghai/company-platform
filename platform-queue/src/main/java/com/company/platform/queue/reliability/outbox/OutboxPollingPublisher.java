package com.company.platform.queue.reliability.outbox;

import com.company.platform.queue.api.model.MessageEnvelope;
import com.company.platform.queue.api.publish.PublishResult;
import com.company.platform.queue.application.port.out.PreparedMessage;
import com.company.platform.queue.application.registry.MessagePublisherRegistry;
import com.company.platform.queue.application.registry.QueueBrokerRegistry;
import com.company.platform.queue.application.registry.QueueDestinationRegistry;
import com.company.platform.queue.autoconfigure.properties.PlatformQueueProperties;
import com.company.platform.queue.domain.result.PublishStatus;
import com.company.platform.queue.serialization.MessageSerializationContext;
import com.company.platform.queue.serialization.registry.MessageSerializerRegistry;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Bounded, fenced outbox dispatch cycle. Scheduling is deliberately external so
 * applications can use their existing scheduler/leader-election policy.
 */
public final class OutboxPollingPublisher {
    private static final String FAILED_CODE = "QUEUE.OUTBOX_PUBLISH_FAILED";
    private static final String DEAD_CODE = "QUEUE.OUTBOX_MAX_ATTEMPTS";

    private final PlatformQueueProperties properties;
    private final OutboxMessageStore store;
    private final QueueDestinationRegistry destinations;
    private final QueueBrokerRegistry brokers;
    private final MessagePublisherRegistry publishers;
    private final MessageSerializerRegistry serializers;
    private final AtomicBoolean running = new AtomicBoolean();

    public OutboxPollingPublisher(
        PlatformQueueProperties properties,
        OutboxMessageStore store,
        QueueDestinationRegistry destinations,
        QueueBrokerRegistry brokers,
        MessagePublisherRegistry publishers,
        MessageSerializerRegistry serializers
    ) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.store = Objects.requireNonNull(store, "store");
        this.destinations = Objects.requireNonNull(destinations, "destinations");
        this.brokers = Objects.requireNonNull(brokers, "brokers");
        this.publishers = Objects.requireNonNull(publishers, "publishers");
        this.serializers = Objects.requireNonNull(serializers, "serializers");
    }

    /**
     * Runs at most one claim batch. Concurrent invocations on the same instance
     * are coalesced; cross-instance safety is provided by the store lease token.
     *
     * @return number of claimed records
     */
    public int runOnce() {
        if (!running.compareAndSet(false, true)) {
            return 0;
        }
        try {
            var reliability = properties.getReliability();
            var records = store.claimBatch(
                reliability.getOutboxBatchSize(), reliability.getLockTimeout());
            records.forEach(this::dispatch);
            return records.size();
        } finally {
            running.set(false);
        }
    }

    private void dispatch(OutboxRecord record) {
        if (record.attemptCount() >= properties.getReliability().getMaxAttempts()) {
            store.markFailed(
                record.id(), record.ownerId(), record.fencingToken(), DEAD_CODE);
            return;
        }
        try {
            var destination = destinations.requireEnabled(record.destination());
            var broker = brokers.require(destination.getBroker());
            var format = destination.getSerialization().getFormat();
            var context = new MessageSerializationContext(
                record.eventType(), record.schemaVersion(),
                properties.getDefaults().getContentType(),
                properties.getDefaults().getMaxEnvelopeBytes());
            @SuppressWarnings("unchecked")
            MessageEnvelope<Object> envelope = serializers.require(format)
                .deserialize(record.payload(), MessageEnvelope.class, context);
            Duration timeout = destination.getSendTimeout();
            PreparedMessage prepared = new PreparedMessage(
                destination.getBroker(), record.destination(), record.messageKey(),
                null, null, envelope, record.payload());
            PublishResult result = publishers.require(broker.getProvider())
                .publish(prepared, timeout).toCompletableFuture()
                .get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (result.status() == PublishStatus.CONFIRMED) {
                store.markPublished(
                    record.id(), record.ownerId(), record.fencingToken(), result);
            } else {
                store.markFailed(
                    record.id(), record.ownerId(), record.fencingToken(),
                    result.failureCode() == null ? FAILED_CODE : result.failureCode());
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            store.markFailed(
                record.id(), record.ownerId(), record.fencingToken(), FAILED_CODE);
        } catch (Exception exception) {
            store.markFailed(
                record.id(), record.ownerId(), record.fencingToken(), FAILED_CODE);
        }
    }
}
