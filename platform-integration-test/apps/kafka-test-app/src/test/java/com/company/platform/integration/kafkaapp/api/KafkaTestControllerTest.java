package com.company.platform.integration.kafkaapp.api;

import com.company.platform.core.time.TimeProvider;
import com.company.platform.integration.kafkaapp.service.KafkaMessageProbe;
import com.company.platform.queue.api.publish.MessagePublisher;
import com.company.platform.queue.api.publish.PublishRequest;
import com.company.platform.queue.api.publish.PublishResult;
import com.company.platform.queue.domain.model.QueueProviderType;
import com.company.platform.queue.domain.result.PublishStatus;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KafkaTestControllerTest {
    private static final Instant NOW = Instant.parse("2026-08-02T00:00:00Z");

    @Test
    void reusesCallerMessageIdOnHttpRetry() {
        MessagePublisher publisher = mock(MessagePublisher.class);
        TimeProvider time = mock(TimeProvider.class);
        when(time.nowInstant()).thenReturn(NOW);
        when(publisher.publish(any(PublishRequest.class)))
            .thenReturn(confirmed("manual-001"));
        KafkaTestController controller = new KafkaTestController(
            publisher, new KafkaMessageProbe(), time);

        var response = controller.publish(
            new KafkaPublishRequest("manual-001", "customer-1", "hello"));

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().messageId()).isEqualTo("manual-001");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<PublishRequest<?>> captor =
            ArgumentCaptor.forClass((Class) PublishRequest.class);
        verify(publisher).publish(captor.capture());
        assertThat(captor.getValue().messageId()).isEqualTo("manual-001");
        assertThat(captor.getValue().key()).isEqualTo("customer-1");
    }

    private PublishResult confirmed(String messageId) {
        return new PublishResult(
            PublishStatus.CONFIRMED,
            QueueProviderType.KAFKA,
            "kafka-main",
            "kafka-test-events",
            "platform.test.events.v1",
            messageId,
            0,
            1L,
            null,
            true,
            false,
            1,
            Duration.ofMillis(10),
            NOW,
            null,
            null);
    }
}
