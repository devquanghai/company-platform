package com.company.platform.queue.api.publish;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class PublishRequest<T> {
    private static final int MAX_METADATA_LENGTH = 8 * 1024;
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
    private final Duration timeout;

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
        if (schemaVersion < 0) {
            throw new IllegalArgumentException("schemaVersion must not be negative");
        }
        validateMetadata(messageId, "messageId");
        validateMetadata(eventId, "eventId");
        validateMetadata(correlationId, "correlationId");
        validateMetadata(causationId, "causationId");
        validateMetadata(eventType, "eventType");
        headers = Map.copyOf(builder.headers);
        timeout = builder.timeout;
        if (timeout != null && (timeout.isZero() || timeout.isNegative())) {
            throw new IllegalArgumentException("timeout must be positive");
        }
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
    public Duration timeout() { return timeout; }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    private static void validateMetadata(String value, String field) {
        if (value != null && value.length() > MAX_METADATA_LENGTH) {
            throw new IllegalArgumentException(field + " is too large");
        }
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
        private int schemaVersion;
        private final Map<String, String> headers = new LinkedHashMap<>();
        private Duration timeout;

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
        public Builder<T> headers(Map<String, String> values) {
            headers.putAll(Objects.requireNonNull(values, "values"));
            return this;
        }
        public Builder<T> timeout(Duration value) { timeout = value; return this; }
        public PublishRequest<T> build() { return new PublishRequest<>(this); }
    }
}
