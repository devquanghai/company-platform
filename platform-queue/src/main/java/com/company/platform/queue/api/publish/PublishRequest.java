package com.company.platform.queue.api.publish;

import com.company.platform.queue.domain.model.PublishMode;
import com.company.platform.queue.serialization.MessageSerializationFormat;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class PublishRequest<T> {
    private final String destination;
    private final Object key;
    private final T payload;
    private final String messageId;
    private final String eventId;
    private final String correlationId;
    private final String causationId;
    private final String eventType;
    private final int schemaVersion;
    private final Map<String, String> headers;
    private final Instant deliverAt;
    private final Integer partition;
    private final String routingKey;
    private final Duration timeout;
    private final PublishMode mode;
    private final MessageSerializationFormat serialization;

    private PublishRequest(Builder<T> builder) {
        destination = requireText(builder.destination, "destination");
        payload = Objects.requireNonNull(builder.payload, "payload");
        key = builder.key;
        messageId = builder.messageId;
        eventId = builder.eventId;
        correlationId = builder.correlationId;
        causationId = builder.causationId;
        eventType = builder.eventType;
        schemaVersion = builder.schemaVersion;
        headers = Map.copyOf(builder.headers);
        deliverAt = builder.deliverAt;
        partition = builder.partition;
        routingKey = builder.routingKey;
        timeout = builder.timeout;
        mode = Objects.requireNonNull(builder.mode, "mode");
        serialization = builder.serialization;
    }

    public static <T> Builder<T> builder(T payload) {
        return new Builder<>(payload);
    }

    public String destination() { return destination; }
    public Object key() { return key; }
    public T payload() { return payload; }
    public String messageId() { return messageId; }
    public String eventId() { return eventId; }
    public String correlationId() { return correlationId; }
    public String causationId() { return causationId; }
    public String eventType() { return eventType; }
    public int schemaVersion() { return schemaVersion; }
    public Map<String, String> headers() { return headers; }
    public Instant deliverAt() { return deliverAt; }
    public Integer partition() { return partition; }
    public String routingKey() { return routingKey; }
    public Duration timeout() { return timeout; }
    public PublishMode mode() { return mode; }
    public MessageSerializationFormat serialization() { return serialization; }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    public static final class Builder<T> {
        private String destination;
        private Object key;
        private final T payload;
        private String messageId;
        private String eventId;
        private String correlationId;
        private String causationId;
        private String eventType;
        private int schemaVersion = 1;
        private final Map<String, String> headers = new LinkedHashMap<>();
        private Instant deliverAt;
        private Integer partition;
        private String routingKey;
        private Duration timeout;
        private PublishMode mode = PublishMode.DIRECT;
        private MessageSerializationFormat serialization;

        private Builder(T payload) { this.payload = payload; }
        public Builder<T> destination(String value) { destination = value; return this; }
        public Builder<T> key(Object value) { key = value; return this; }
        public Builder<T> messageId(String value) { messageId = value; return this; }
        public Builder<T> eventId(String value) { eventId = value; return this; }
        public Builder<T> correlationId(String value) { correlationId = value; return this; }
        public Builder<T> causationId(String value) { causationId = value; return this; }
        public Builder<T> eventType(String value) { eventType = value; return this; }
        public Builder<T> schemaVersion(int value) { schemaVersion = value; return this; }
        public Builder<T> header(String name, String value) { headers.put(name, value); return this; }
        public Builder<T> deliverAt(Instant value) { deliverAt = value; return this; }
        public Builder<T> partition(Integer value) { partition = value; return this; }
        public Builder<T> routingKey(String value) { routingKey = value; return this; }
        public Builder<T> timeout(Duration value) { timeout = value; return this; }
        public Builder<T> mode(PublishMode value) { mode = value; return this; }
        public Builder<T> serialization(MessageSerializationFormat value) {
            serialization = value; return this;
        }
        public PublishRequest<T> build() { return new PublishRequest<>(this); }
    }
}
