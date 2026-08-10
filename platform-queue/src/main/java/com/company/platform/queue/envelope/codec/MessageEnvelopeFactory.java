package com.company.platform.queue.envelope.codec;

import com.company.platform.core.context.RequestContextProvider;
import com.company.platform.core.time.TimeProvider;
import com.company.platform.core.trace.TraceContextProvider;
import com.company.platform.queue.api.model.MessageEnvelope;
import com.company.platform.queue.api.model.MessageMetadata;
import com.company.platform.queue.api.publish.PublishRequest;
import com.company.platform.queue.autoconfigure.properties.DestinationProperties;
import com.company.platform.queue.autoconfigure.properties.PlatformQueueProperties;
import com.company.platform.queue.envelope.validation.SafeHeaderPolicy;
import com.company.platform.queue.configuration.internal.QueueMessageDefaults;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class MessageEnvelopeFactory {
    private final PlatformQueueProperties properties;
    private final TimeProvider timeProvider;
    private final RequestContextProvider requestContext;
    private final TraceContextProvider traceContext;
    private final SafeHeaderPolicy headerPolicy;

    public MessageEnvelopeFactory(
        PlatformQueueProperties properties,
        TimeProvider timeProvider,
        RequestContextProvider requestContext,
        TraceContextProvider traceContext,
        SafeHeaderPolicy headerPolicy
    ) {
        this.properties = properties;
        this.timeProvider = timeProvider;
        this.requestContext = requestContext;
        this.traceContext = traceContext;
        this.headerPolicy = headerPolicy;
    }

    public <T> MessageEnvelope<T> create(
        PublishRequest<T> request, DestinationProperties destination
    ) {
        Instant now = timeProvider.nowInstant();
        String messageId = textOr(request.messageId(), UUID.randomUUID().toString());
        String correlationId = textOr(
            request.correlationId(),
            requestContext == null ? null : requestContext.getCorrelationId());
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = messageId;
        }
        var trace = traceContext == null ? null : traceContext.getCurrentContext();
        String eventType = textOr(
            request.eventType(),
            destination.getSerialization().getEventType());
        if (eventType == null) {
            eventType = request.payload().getClass().getSimpleName();
        }
        int version = request.schemaVersion() > 0
            ? request.schemaVersion() : destination.getSerialization().getSchemaVersion();
        MessageMetadata metadata = new MessageMetadata(
            messageId, request.eventId(), correlationId, request.causationId(),
            trace == null ? null : trace.getTraceId(),
            trace == null ? null : trace.getSpanId(),
            properties.getApplicationName(), request.destination(), eventType,
            version, now, now, QueueMessageDefaults.CONTENT_TYPE,
            headerPolicy.sanitize(request.headers()));
        return new MessageEnvelope<>(metadata, request.payload());
    }

    private String textOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
