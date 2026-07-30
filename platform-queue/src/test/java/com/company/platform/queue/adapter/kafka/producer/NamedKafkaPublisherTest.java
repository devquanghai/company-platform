package com.company.platform.queue.adapter.kafka.producer;

import com.company.platform.queue.QueueTestSupport;
import com.company.platform.queue.application.registry.QueueDestinationRegistry;
import com.company.platform.queue.domain.result.PublishStatus;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NamedKafkaPublisherTest {

    @Test
    void confirmsPublishAndExposesOperations() {
        KafkaTemplate<String, byte[]> template = mock(KafkaTemplate.class);
        DefaultKafkaProducerFactory<String, byte[]> factory =
            mock(DefaultKafkaProducerFactory.class);
        RecordMetadata metadata = new RecordMetadata(
            new org.apache.kafka.common.TopicPartition("events.v1", 2),
            42L, 0, 0L, -1, -1);
        SendResult<String, byte[]> sendResult = new SendResult<>(
            new org.apache.kafka.clients.producer.ProducerRecord<>(
                "events.v1", "key", new byte[] {1}),
            metadata);
        when(template.send(any(org.apache.kafka.clients.producer.ProducerRecord.class)))
            .thenReturn(CompletableFuture.completedFuture(sendResult));
        when(template.partitionsFor("events.v1")).thenReturn(java.util.List.of(
            new org.apache.kafka.common.PartitionInfo(
                "events.v1", 0, null,
                new org.apache.kafka.common.Node[0],
                new org.apache.kafka.common.Node[0])));
        NamedKafkaPublisher publisher = publisher(template, factory);

        var result = publisher.publish(
            QueueTestSupport.prepared("kafka-main"), Duration.ofSeconds(1))
            .toCompletableFuture().join();

        assertThat(result.status()).isEqualTo(PublishStatus.CONFIRMED);
        assertThat(result.partition()).isEqualTo(2);
        assertThat(result.offset()).isEqualTo(42);
        assertThat(publisher.partitionCount(
            "kafka-main", "events.v1", Duration.ofSeconds(1))).isEqualTo(1);
        publisher.flush("kafka-main");
        verify(template).flush();
        publisher.destroy();
        verify(factory).destroy();
    }

    @Test
    void rejectsUnsafeOverridesAndUnknownBroker() {
        KafkaTemplate<String, byte[]> template = mock(KafkaTemplate.class);
        var destination = QueueTestSupport.kafkaDestination();
        destination.getKafka().setRequireKey(true);
        NamedKafkaPublisher publisher = new NamedKafkaPublisher(
            Map.of("kafka-main", new KafkaPublisherResources(
                mock(DefaultKafkaProducerFactory.class), template)),
            new QueueDestinationRegistry(Map.of("events", destination)),
            QueueTestSupport.time());
        var noKey = new com.company.platform.queue.application.port.out.PreparedMessage(
            "kafka-main", "events", null, null, null,
            QueueTestSupport.envelope(), new byte[] {1});
        assertThatThrownBy(() -> publisher.publish(noKey, Duration.ofSeconds(1))
            .toCompletableFuture().join()).hasRootCauseInstanceOf(
                IllegalArgumentException.class);

        destination.getKafka().setRequireKey(false);
        var partition = new com.company.platform.queue.application.port.out.PreparedMessage(
            "kafka-main", "events", "key", 1, null,
            QueueTestSupport.envelope(), new byte[] {1});
        assertThatThrownBy(() -> publisher.publish(partition, Duration.ofSeconds(1))
            .toCompletableFuture().join()).hasRootCauseInstanceOf(
                IllegalArgumentException.class);
        assertThatThrownBy(() -> publisher.flush("missing"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    private NamedKafkaPublisher publisher(
        KafkaTemplate<String, byte[]> template,
        DefaultKafkaProducerFactory<String, byte[]> factory
    ) {
        return new NamedKafkaPublisher(
            Map.of("kafka-main", new KafkaPublisherResources(factory, template)),
            new QueueDestinationRegistry(Map.of(
                "events", QueueTestSupport.kafkaDestination())),
            QueueTestSupport.time());
    }
}
