package com.company.platform.queue.api.kafka;

import com.company.platform.queue.api.publish.PublishRequest;
import com.company.platform.queue.domain.model.PublishMode;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class KafkaPublishMessage<T> {
    private final T payload;
    private final String key;
    private final String messageId;
    private final String eventId;
    private final String correlationId;
    private final String causationId;
    private final String eventType;
    private final int schemaVersion;
    private final Map<String, String> headers;
    private final Integer partition;
    private final Duration timeout;
    private final PublishMode mode;

    private KafkaPublishMessage(Builder<T> builder) {
        payload = Objects.requireNonNull(builder.payload, "payload");
        key = builder.key;
        messageId = builder.messageId;
        eventId = builder.eventId;
        correlationId = builder.correlationId;
        causationId = builder.causationId;
        eventType = builder.eventType;
        schemaVersion = builder.schemaVersion;
        headers = Map.copyOf(builder.headers);
        partition = builder.partition;
        timeout = builder.timeout;
        mode = builder.mode;
    }

    public static <T> Builder<T> builder(T payload) {
        return new Builder<>(payload);
    }

    public T payload() { return payload; }
    public String key() { return key; }
    public String messageId() { return messageId; }
    public String eventId() { return eventId; }
    public String correlationId() { return correlationId; }
    public String causationId() { return causationId; }
    public String eventType() { return eventType; }
    public int schemaVersion() { return schemaVersion; }
    public Map<String, String> headers() { return headers; }
    public Integer partition() { return partition; }
    public Duration timeout() { return timeout; }
    public PublishMode mode() { return mode; }

    PublishRequest<T> toRequest(String destination) {
        PublishRequest.Builder<T> request = PublishRequest.builder(payload)
            .destination(destination)
            .key(key)
            .messageId(messageId)
            .eventId(eventId)
            .correlationId(correlationId)
            .causationId(causationId)
            .eventType(eventType)
            .schemaVersion(schemaVersion)
            .headers(headers)
            .partition(partition)
            .timeout(timeout);
        if (mode != null) {
            request.mode(mode);
        }
        return request.build();
    }

    public static final class Builder<T> {
        private final T payload;
        private String key;
        private String messageId;
        private String eventId;
        private String correlationId;
        private String causationId;
        private String eventType;
        private int schemaVersion;
        private final Map<String, String> headers = new LinkedHashMap<>();
        private Integer partition;
        private Duration timeout;
        private PublishMode mode;

        private Builder(T payload) { this.payload = payload; }
        public Builder<T> key(String value) { key = value; return this; }
        public Builder<T> messageId(String value) { messageId = value; return this; }
        public Builder<T> eventId(String value) { eventId = value; return this; }
        public Builder<T> correlationId(String value) { correlationId = value; return this; }
        public Builder<T> causationId(String value) { causationId = value; return this; }
        public Builder<T> eventType(String value) { eventType = value; return this; }
        public Builder<T> schemaVersion(int value) { schemaVersion = value; return this; }
        public Builder<T> header(String name, String value) {
            headers.put(name, value); return this;
        }
        public Builder<T> headers(Map<String, String> values) {
            headers.putAll(Objects.requireNonNull(values, "values")); return this;
        }
        public Builder<T> partition(Integer value) { partition = value; return this; }
        public Builder<T> timeout(Duration value) { timeout = value; return this; }
        public Builder<T> mode(PublishMode value) { mode = value; return this; }
        public KafkaPublishMessage<T> build() { return new KafkaPublishMessage<>(this); }
    }
}
