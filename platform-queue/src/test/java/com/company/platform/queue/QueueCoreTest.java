package com.company.platform.queue;

import com.company.platform.core.json.JsonMapperHelper;
import com.company.platform.queue.api.model.MessageEnvelope;
import com.company.platform.queue.api.model.MessageMetadata;
import com.company.platform.queue.api.publish.PublishRequest;
import com.company.platform.queue.domain.model.PublishMode;
import com.company.platform.queue.envelope.validation.MessageLimits;
import com.company.platform.queue.envelope.validation.SafeHeaderPolicy;
import com.company.platform.queue.serialization.MessageSerializationContext;
import com.company.platform.queue.serialization.MessageSerializationFormat;
import com.company.platform.queue.serialization.json.JsonMessageSerializer;
import com.company.platform.queue.serialization.registry.DefaultMessageSerializerRegistry;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QueueCoreTest {

    @Test
    void createsImmutableEnvelopeAndRequest() {
        Instant now = Instant.parse("2026-07-30T00:00:00Z");
        MessageMetadata metadata = new MessageMetadata(
            "message-1", "event-1", "correlation-1", null, "trace", "span",
            "orders", "order-created", "OrderCreated", 1, now, now,
            "application/json", Map.of("safe", "value"));
        MessageEnvelope<Map<String, String>> envelope =
            new MessageEnvelope<>(metadata, Map.of("id", "1"));
        PublishRequest<Map<String, String>> request =
            PublishRequest.builder(envelope.payload())
                .destination("order-created")
                .key("order-1")
                .messageId("message-1")
                .eventId("event-1")
                .correlationId("correlation-1")
                .causationId("cause-1")
                .eventType("OrderCreated")
                .schemaVersion(2)
                .header("tenant", "internal")
                .partition(1)
                .routingKey("orders.created")
                .mode(PublishMode.OUTBOX)
                .serialization(MessageSerializationFormat.JSON)
                .build();

        assertThat(envelope.metadata().headers()).containsEntry("safe", "value");
        assertThat(request.destination()).isEqualTo("order-created");
        assertThat(request.key()).isEqualTo("order-1");
        assertThat(request.schemaVersion()).isEqualTo(2);
        assertThat(request.mode()).isEqualTo(PublishMode.OUTBOX);
        assertThat(request.headers()).containsEntry("tenant", "internal");
    }

    @Test
    void rejectsInvalidMetadataAndEnvelope() {
        Instant now = Instant.now();
        assertThatThrownBy(() -> new MessageMetadata(
            " ", null, null, null, null, null, "app", "dest", "type",
            1, now, now, "json", Map.of()))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MessageMetadata(
            "id", null, null, null, null, null, "app", "dest", "type",
            0, now, now, "json", Map.of()))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MessageEnvelope<>(null, "payload"))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> PublishRequest.builder("x").destination(" ").build())
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validatesAndNormalizesSafeHeaders() {
        SafeHeaderPolicy policy = new SafeHeaderPolicy(
            MessageLimits.DEFAULT, Set.of("tenant-id", "region"));
        assertThat(policy.sanitize(Map.of("Tenant-Id", "tenant-a")))
            .containsEntry("tenant-id", "tenant-a");
        assertThatThrownBy(() -> policy.sanitize(Map.of("message-id", "override")))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy.sanitize(Map.of("authorization", "secret")))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy.sanitize(Map.of("tenant-id", "bad\nvalue")))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MessageLimits(129, 1, 1, 1, 1))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void serializesJsonWithPayloadLimitAndRegistry() {
        JsonMessageSerializer serializer =
            new JsonMessageSerializer(new JsonMapperHelper(JsonMapper.builder().build()));
        MessageSerializationContext context =
            new MessageSerializationContext("Value", 1, "application/json", 100);
        byte[] bytes = serializer.serialize(Map.of("value", "ok"), context);
        assertThat(serializer.deserialize(bytes, Map.class, context))
            .containsEntry("value", "ok");
        DefaultMessageSerializerRegistry registry =
            new DefaultMessageSerializerRegistry(List.of(serializer));
        assertThat(registry.require(MessageSerializationFormat.JSON)).isSameAs(serializer);
        assertThatThrownBy(() ->
            new DefaultMessageSerializerRegistry(List.of(serializer, serializer)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
            registry.require(MessageSerializationFormat.AVRO))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
            serializer.serialize("too-long", new MessageSerializationContext(
                "Value", 1, "application/json", 2)))
            .hasMessageContaining("serialization failed");
    }
}
