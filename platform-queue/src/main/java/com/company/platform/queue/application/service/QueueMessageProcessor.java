package com.company.platform.queue.application.service;

import com.company.platform.queue.api.consume.MessageContext;
import com.company.platform.queue.api.consume.MessageHandlingResult;
import com.company.platform.queue.application.registry.QueueListenerEndpoint;
import com.company.platform.queue.autoconfigure.properties.DestinationProperties;
import com.company.platform.queue.autoconfigure.properties.PlatformQueueProperties;
import com.company.platform.queue.autoconfigure.properties.SubscriptionProperties;
import com.company.platform.queue.domain.policy.RetryDecision;
import com.company.platform.queue.reliability.inbox.InboxAcquireStatus;
import com.company.platform.queue.reliability.inbox.InboxAcquireResult;
import com.company.platform.queue.reliability.inbox.InboxStore;
import com.company.platform.queue.reliability.retry.MessageFailureContext;
import com.company.platform.queue.reliability.retry.MessageRetryDecisionPolicy;
import com.company.platform.queue.serialization.MessageSerializationContext;
import com.company.platform.queue.serialization.registry.MessageSerializerRegistry;

import java.lang.reflect.InvocationTargetException;
import java.util.Objects;

public final class QueueMessageProcessor {
    private final PlatformQueueProperties properties;
    private final MessageSerializerRegistry serializers;
    private final MessageRetryDecisionPolicy retryPolicy;
    private final InboxStore inbox;

    public QueueMessageProcessor(
        PlatformQueueProperties properties,
        MessageSerializerRegistry serializers,
        MessageRetryDecisionPolicy retryPolicy,
        InboxStore inbox
    ) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.serializers = Objects.requireNonNull(serializers, "serializers");
        this.retryPolicy = Objects.requireNonNull(retryPolicy, "retryPolicy");
        this.inbox = inbox;
    }

    public ListenerInvocationResult process(
        QueueListenerEndpoint endpoint,
        SubscriptionProperties subscription,
        DestinationProperties destination,
        byte[] body,
        MessageContext transportContext
    ) {
        InboxAcquireResult acquired = null;
        String acquiredMessageId = null;
        try {
            var format = destination.getSerialization().getFormat();
            var serializationContext = new MessageSerializationContext(
                transportContext.destination(),
                destination.getSerialization().getSchemaVersion(),
                properties.getDefaults().getContentType(),
                properties.getDefaults().getMaxEnvelopeBytes());
            var envelope = serializers.require(format).deserializeEnvelope(
                body, endpoint.payloadType(), serializationContext);
            String messageId = envelope.metadata().messageId();
            if (subscription.isIdempotencyEnabled()) {
                if (inbox == null) {
                    throw new IllegalStateException(
                        "idempotent subscription requires InboxStore");
                }
                acquired = inbox.acquire(
                    endpoint.handlerId(), messageId,
                    properties.getReliability().getLockTimeout());
                if (acquired.status() == InboxAcquireStatus.DUPLICATE_PROCESSED) {
                    return ListenerInvocationResult.handled(MessageHandlingResult.ACK);
                }
                if (acquired.status() == InboxAcquireStatus.PROCESSING_BY_ANOTHER) {
                    return ListenerInvocationResult.handled(MessageHandlingResult.RETRY);
                }
                acquiredMessageId = messageId;
            }
            MessageContext context = withEnvelope(transportContext, envelope.metadata());
            MessageHandlingResult result = invoke(
                endpoint, envelope.payload(), context);
            if (subscription.isIdempotencyEnabled()) {
                if (result == MessageHandlingResult.ACK) {
                    inbox.markProcessed(
                        endpoint.handlerId(), messageId,
                        acquired.ownerId(), acquired.fencingToken());
                } else {
                    inbox.markFailed(
                        endpoint.handlerId(), messageId,
                        acquired.ownerId(), acquired.fencingToken(),
                        "QUEUE.HANDLER_NOT_ACKNOWLEDGED");
                }
            }
            return ListenerInvocationResult.handled(result);
        } catch (RuntimeException exception) {
            if (acquired != null && acquiredMessageId != null) {
                inbox.markFailed(
                    endpoint.handlerId(), acquiredMessageId,
                    acquired.ownerId(), acquired.fencingToken(),
                    "QUEUE.LISTENER_PROCESSING_FAILED");
            }
            return ListenerInvocationResult.failed(retryPolicy.evaluate(
                new MessageFailureContext(
                    transportContext.provider(), transportContext.destination(),
                    transportContext.deliveryAttempt(), exception,
                    exception.getClass().getSimpleName(), null,
                    !"QUEUE.DESERIALIZATION_FAILED".equals(errorCode(exception)),
                    transportContext.redelivered())));
        }
    }

    private MessageHandlingResult invoke(
        QueueListenerEndpoint endpoint, Object payload, MessageContext context
    ) {
        try {
            return (MessageHandlingResult) endpoint.method().invoke(
                endpoint.bean(), payload, context);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("queue listener is not accessible", exception);
        } catch (InvocationTargetException exception) {
            Throwable target = exception.getTargetException();
            if (target instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new IllegalStateException("queue listener failed", target);
        }
    }

    private MessageContext withEnvelope(
        MessageContext context,
        com.company.platform.queue.api.model.MessageMetadata metadata
    ) {
        return new MessageContext(
            context.provider(), context.broker(), context.subscription(),
            metadata.destination(), context.physicalDestination(),
            metadata.messageId(), metadata.correlationId(), metadata.causationId(),
            metadata.headers(), context.receivedAt(), context.deliveryAttempt(),
            context.partition(), context.offset(), context.consumerGroup(),
            context.exchange(), context.routingKey(), context.redelivered(),
            metadata.traceId());
    }

    private String errorCode(RuntimeException exception) {
        return exception instanceof com.company.platform.core.exception.PlatformException platform
            ? platform.errorCode() : null;
    }
}
