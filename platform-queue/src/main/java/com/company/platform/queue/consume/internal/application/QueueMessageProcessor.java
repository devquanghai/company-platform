package com.company.platform.queue.consume.internal.application;

import com.company.platform.queue.api.consume.MessageContext;
import com.company.platform.queue.api.consume.MessageHandlingResult;
import com.company.platform.queue.consume.internal.application.QueueListenerEndpoint;
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
import com.company.platform.queue.configuration.internal.QueueMessageDefaults;
import com.company.platform.queue.envelope.validation.SafeHeaderPolicy;

import java.lang.reflect.InvocationTargetException;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.beans.factory.DisposableBean;

public final class QueueMessageProcessor implements DisposableBean {
    private final PlatformQueueProperties properties;
    private final MessageSerializerRegistry serializers;
    private final MessageRetryDecisionPolicy retryPolicy;
    private final InboxStore inbox;
    private final SafeHeaderPolicy headerPolicy;
    private final ScheduledExecutorService inboxHeartbeat;

    public QueueMessageProcessor(
        PlatformQueueProperties properties,
        MessageSerializerRegistry serializers,
        MessageRetryDecisionPolicy retryPolicy,
        InboxStore inbox,
        SafeHeaderPolicy headerPolicy
    ) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.serializers = Objects.requireNonNull(serializers, "serializers");
        this.retryPolicy = Objects.requireNonNull(retryPolicy, "retryPolicy");
        this.inbox = inbox;
        this.headerPolicy = Objects.requireNonNull(headerPolicy, "headerPolicy");
        this.inboxHeartbeat = inbox == null ? null
            : Executors.newScheduledThreadPool(heartbeatThreads(properties), runnable -> {
                Thread thread = new Thread(runnable, "platform-queue-inbox-heartbeat");
                thread.setDaemon(true);
                return thread;
            });
    }

    private int heartbeatThreads(PlatformQueueProperties configuration) {
        int configured = configuration.getSubscriptions().values().stream()
            .filter(com.company.platform.queue.autoconfigure.properties.SubscriptionProperties
                ::isEnabled)
            .mapToInt(subscription -> Math.max(
                subscription.getKafka().getConcurrency(),
                subscription.getRabbit().getMaxConcurrency()))
            .sum();
        return Math.max(2, Math.min(64, configured));
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
        ScheduledFuture<?> heartbeat = null;
        AtomicReference<RuntimeException> renewalFailure = new AtomicReference<>();
        try {
            var format = destination.getSerialization().getFormat();
            var serializationContext = new MessageSerializationContext(
                transportContext.destination(),
                destination.getSerialization().getSchemaVersion(),
                QueueMessageDefaults.CONTENT_TYPE,
                QueueMessageDefaults.limits(properties.getMessage()).maxEnvelopeBytes());
            var serializer = serializers.require(format);
            var envelope = serializer.deserializeEnvelope(
                body, endpoint.payloadType(), serializationContext);
            if (!subscription.getDestination().equals(envelope.metadata().destination())) {
                throw new IllegalArgumentException(
                    "envelope destination does not match subscription destination");
            }
            var safeHeaders = headerPolicy.sanitize(envelope.metadata().headers());
            serializer.serialize(envelope.payload(), new MessageSerializationContext(
                envelope.metadata().eventType(), envelope.metadata().schemaVersion(),
                envelope.metadata().contentType(),
                QueueMessageDefaults.limits(properties.getMessage()).maxPayloadBytes()));
            String messageId = envelope.metadata().messageId();
            if (subscription.isIdempotencyEnabled()) {
                if (inbox == null) {
                    throw new IllegalStateException(
                        "idempotent subscription requires InboxStore");
                }
                acquired = inbox.acquire(
                    endpoint.handlerId(), messageId,
                    properties.getDelivery().getProcessingLockTimeout());
                if (acquired.status() == InboxAcquireStatus.DUPLICATE_PROCESSED) {
                    return ListenerInvocationResult.handled(MessageHandlingResult.ACK);
                }
                if (acquired.status() == InboxAcquireStatus.PROCESSING_BY_ANOTHER) {
                    return ListenerInvocationResult.contended(acquired.lockedUntil());
                }
                acquiredMessageId = messageId;
                InboxAcquireResult owned = acquired;
                long heartbeatMillis = Math.max(100,
                    properties.getDelivery().getProcessingLockTimeout().toMillis() / 3);
                heartbeat = inboxHeartbeat.scheduleWithFixedDelay(() -> {
                    try {
                        inbox.renew(
                            endpoint.handlerId(), messageId, owned.ownerId(),
                            owned.fencingToken(),
                            properties.getDelivery().getProcessingLockTimeout());
                    } catch (RuntimeException failure) {
                        renewalFailure.compareAndSet(null, failure);
                    }
                }, heartbeatMillis, heartbeatMillis, TimeUnit.MILLISECONDS);
            }
            MessageContext context = withEnvelope(
                transportContext, envelope.metadata(), safeHeaders,
                subscription.getDestination());
            MessageHandlingResult result = invoke(
                endpoint, envelope.payload(), context);
            if (renewalFailure.get() != null) {
                throw renewalFailure.get();
            }
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
        } finally {
            if (heartbeat != null) {
                heartbeat.cancel(false);
            }
        }
    }

    @Override
    public void destroy() {
        if (inboxHeartbeat != null) {
            inboxHeartbeat.shutdownNow();
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
        com.company.platform.queue.api.model.MessageMetadata metadata,
        java.util.Map<String, String> safeHeaders,
        String configuredDestination
    ) {
        return new MessageContext(
            context.provider(), context.broker(), context.subscription(),
            configuredDestination, context.physicalDestination(),
            metadata.messageId(), metadata.correlationId(), metadata.causationId(),
            safeHeaders, context.receivedAt(), context.deliveryAttempt(),
            context.partition(), context.offset(), context.consumerGroup(),
            context.exchange(), context.routingKey(), context.redelivered(),
            metadata.traceId());
    }

    private String errorCode(RuntimeException exception) {
        return exception instanceof com.company.platform.core.exception.PlatformException platform
            ? platform.errorCode() : null;
    }
}
