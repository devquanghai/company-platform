package com.company.platform.queue.adapter.rabbit.producer;

import com.company.platform.queue.QueueTestSupport;
import com.company.platform.queue.application.registry.QueueDestinationRegistry;
import com.company.platform.queue.domain.result.PublishStatus;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NamedRabbitPublisherTest {

    @Test
    void distinguishesAckNackAndReturn() {
        RabbitTemplate template = mock(RabbitTemplate.class);
        CachingConnectionFactory factory = mock(CachingConnectionFactory.class);
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().complete(
                new CorrelationData.Confirm(true, null));
            return null;
        }).when(template).send(anyString(), anyString(), any(Message.class),
            any(CorrelationData.class));
        NamedRabbitPublisher publisher = publisher(template, factory);
        var confirmed = publisher.publish(
            QueueTestSupport.prepared("rabbit-main"), Duration.ofSeconds(1))
            .toCompletableFuture().join();
        assertThat(confirmed.status()).isEqualTo(PublishStatus.CONFIRMED);
        assertThat(confirmed.routingKey()).isEqualTo("events.created");

        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.setReturned(mock(ReturnedMessage.class));
            correlation.getFuture().complete(
                new CorrelationData.Confirm(true, null));
            return null;
        }).when(template).send(anyString(), anyString(), any(Message.class),
            any(CorrelationData.class));
        assertThat(publisher.publish(
            QueueTestSupport.prepared("rabbit-main"), Duration.ofSeconds(1))
            .toCompletableFuture().join().status()).isEqualTo(PublishStatus.RETURNED);

        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().complete(
                new CorrelationData.Confirm(false, "nack"));
            return null;
        }).when(template).send(anyString(), anyString(), any(Message.class),
            any(CorrelationData.class));
        assertThat(publisher.publish(
            QueueTestSupport.prepared("rabbit-main"), Duration.ofSeconds(1))
            .toCompletableFuture().join().status()).isEqualTo(PublishStatus.REJECTED);

        when(template.waitForConfirms(1000)).thenReturn(true);
        assertThat(publisher.waitForConfirms(
            "rabbit-main", Duration.ofSeconds(1))).isTrue();
        publisher.destroy();
        verify(factory).destroy();
    }

    @Test
    void rejectsOverrideUnknownBrokerAndSendFailure() {
        RabbitTemplate template = mock(RabbitTemplate.class);
        var destination = QueueTestSupport.rabbitDestination();
        NamedRabbitPublisher publisher = new NamedRabbitPublisher(
            Map.of("rabbit-main", new RabbitPublisherResources(
                mock(CachingConnectionFactory.class), template)),
            new QueueDestinationRegistry(Map.of("events", destination)),
            QueueTestSupport.time());
        var override = new com.company.platform.queue.application.port.out.PreparedMessage(
            "rabbit-main", "events", null, null, "override",
            QueueTestSupport.envelope(), new byte[] {1});
        assertThatThrownBy(() -> publisher.publish(override, Duration.ofSeconds(1))
            .toCompletableFuture().join()).hasRootCauseInstanceOf(
                IllegalArgumentException.class);
        assertThatThrownBy(() -> publisher.waitForConfirms(
            "missing", Duration.ofSeconds(1))).isInstanceOf(IllegalArgumentException.class);

        destination.getRabbit().setAllowRoutingKeyOverride(true);
        doThrow(new IllegalStateException("offline")).when(template)
            .send(anyString(), anyString(), any(Message.class),
                any(CorrelationData.class));
        assertThatThrownBy(() -> publisher.publish(override, Duration.ofSeconds(1))
            .toCompletableFuture().join()).hasRootCauseMessage("offline");
    }

    private NamedRabbitPublisher publisher(
        RabbitTemplate template, CachingConnectionFactory factory
    ) {
        return new NamedRabbitPublisher(
            Map.of("rabbit-main", new RabbitPublisherResources(factory, template)),
            new QueueDestinationRegistry(Map.of(
                "events", QueueTestSupport.rabbitDestination())),
            QueueTestSupport.time());
    }
}
