package com.company.platform.queue.publish.internal.adapter.rabbit;

import com.company.platform.core.time.TimeProvider;
import com.company.platform.queue.api.publish.PublishResult;
import com.company.platform.queue.api.rabbit.RabbitQueueOperations;
import com.company.platform.queue.publish.internal.port.out.PreparedMessage;
import com.company.platform.queue.publish.internal.port.out.ProviderMessagePublisher;
import com.company.platform.queue.configuration.internal.registry.QueueDestinationRegistry;
import com.company.platform.queue.domain.model.QueueProviderType;
import com.company.platform.queue.domain.result.PublishStatus;
import com.company.platform.queue.envelope.header.PlatformMessageHeaders;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.beans.factory.DisposableBean;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

public final class NamedRabbitPublisher
    implements ProviderMessagePublisher, RabbitQueueOperations, DisposableBean {

    private final Map<String, RabbitPublisherResources> resources;
    private final QueueDestinationRegistry destinations;
    private final TimeProvider time;

    public NamedRabbitPublisher(
        Map<String, RabbitPublisherResources> resources,
        QueueDestinationRegistry destinations,
        TimeProvider time
    ) {
        this.resources = Map.copyOf(resources);
        this.destinations = destinations;
        this.time = time;
    }

    @Override
    public QueueProviderType provider() {
        return QueueProviderType.RABBITMQ;
    }

    @Override
    public CompletionStage<PublishResult> publish(
        PreparedMessage prepared, Duration timeout
    ) {
        long started = System.nanoTime();
        var resource = require(prepared.broker());
        var destination = destinations.requireEnabled(prepared.destination());
        String routingKey = prepared.routingKey() == null
            ? destination.getRabbit().getRoutingKey() : prepared.routingKey();
        if (prepared.routingKey() != null
            && !destination.getRabbit().isAllowRoutingKeyOverride()) {
            return java.util.concurrent.CompletableFuture.failedFuture(
                new IllegalArgumentException("Rabbit routing-key override is disabled"));
        }
        MessageBuilder builder = MessageBuilder.withBody(prepared.body());
        builder.setContentType(prepared.envelope().metadata().contentType());
        builder.setMessageId(prepared.envelope().metadata().messageId());
        builder.setCorrelationId(prepared.envelope().metadata().correlationId());
        builder.setDeliveryMode(destination.getRabbit().isPersistent()
            ? MessageDeliveryMode.PERSISTENT : MessageDeliveryMode.NON_PERSISTENT);
        put(builder, PlatformMessageHeaders.EVENT_TYPE,
            prepared.envelope().metadata().eventType());
        put(builder, PlatformMessageHeaders.SCHEMA_VERSION,
            prepared.envelope().metadata().schemaVersion());
        put(builder, PlatformMessageHeaders.CAUSATION_ID,
            prepared.envelope().metadata().causationId());
        put(builder, PlatformMessageHeaders.TRACEPARENT,
            traceparent(prepared.envelope()));
        put(builder, PlatformMessageHeaders.SOURCE_APPLICATION,
            prepared.envelope().metadata().sourceApplication());
        prepared.envelope().metadata().headers().forEach(builder::setHeader);
        Message message = builder.build();
        CorrelationData correlation =
            new CorrelationData(UUID.randomUUID().toString());
        try {
            resource.template().send(
                destination.getRabbit().getExchange(), routingKey, message, correlation);
        } catch (RuntimeException exception) {
            return java.util.concurrent.CompletableFuture.failedFuture(exception);
        }
        return correlation.getFuture()
            .orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)
            .thenApply(confirm -> {
                boolean returned = correlation.getReturned() != null;
                PublishStatus status = returned ? PublishStatus.RETURNED
                    : confirm.ack() ? PublishStatus.CONFIRMED : PublishStatus.REJECTED;
                return new PublishResult(
                    status, provider(), prepared.broker(), prepared.destination(),
                    destination.getRabbit().getExchange(),
                    prepared.envelope().metadata().messageId(), null, null,
                    routingKey, confirm.ack(), returned, 1,
                    Duration.ofNanos(System.nanoTime() - started), time.nowInstant(),
                    prepared.envelope().metadata().traceId(),
                    confirm.ack() && !returned ? null : "QUEUE.RABBIT_CONFIRM_FAILED");
            });
    }

    @Override
    public boolean waitForConfirms(String brokerName, Duration timeout) {
        return require(brokerName).template().waitForConfirms(timeout.toMillis());
    }

    @Override
    public void destroy() {
        resources.values().forEach(value -> value.connectionFactory().destroy());
    }

    private RabbitPublisherResources require(String broker) {
        RabbitPublisherResources resource = resources.get(broker);
        if (resource == null) {
            throw new IllegalArgumentException("unknown Rabbit broker");
        }
        return resource;
    }

    private void put(MessageBuilder builder, String name, Object value) {
        if (value != null) {
            builder.setHeader(name, value);
        }
    }

    private String traceparent(
        com.company.platform.queue.api.model.MessageEnvelope<?> envelope
    ) {
        String traceId = envelope.metadata().traceId();
        String spanId = envelope.metadata().spanId();
        return traceId == null || spanId == null
            ? null : "00-" + traceId + "-" + spanId + "-01";
    }
}
