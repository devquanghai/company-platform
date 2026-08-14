package com.company.platform.integration.queue.internal.autoconfigure;

import com.company.platform.core.time.TimeProvider;
import com.company.platform.integration.queue.internal.application.QueueMessageProbe;
import com.company.platform.integration.queue.internal.application.QueuePublishService;
import com.company.platform.integration.queue.internal.port.out.QueueEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.beans.factory.annotation.Value;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.kafka.config.TopicBuilder;

import java.util.List;

/**
 * Local integration stores only. Production applications must replace these
 * beans with transactional database-backed implementations.
 */
@Configuration(proxyBeanMethods = false)
public class IntegrationQueueStoreConfiguration {
    @Bean
    QueueMessageProbe integrationQueueMessageProbe() {
        return new QueueMessageProbe();
    }

    @Bean
    QueuePublishService integrationQueuePublishService(
        List<QueueEventPublisher> publishers, TimeProvider time
    ) {
        return new QueuePublishService(publishers, time);
    }

    @Bean
    NewTopic integrationRealtimeTopic(
        @Value("${KAFKA_REALTIME_TOPIC:queue-realtime}") String name,
        @Value("${KAFKA_TOPIC_PARTITIONS:3}") int partitions,
        @Value("${KAFKA_REPLICATION_FACTOR:1}") short replicas
    ) {
        return TopicBuilder.name(name).partitions(partitions).replicas(replicas).build();
    }

    @Bean
    NewTopic integrationBatchTopic(
        @Value("${KAFKA_BATCH_TOPIC:queue-batch}") String name,
        @Value("${KAFKA_TOPIC_PARTITIONS:3}") int partitions,
        @Value("${KAFKA_REPLICATION_FACTOR:1}") short replicas
    ) {
        return TopicBuilder.name(name).partitions(partitions).replicas(replicas).build();
    }

    @Bean
    NewTopic integrationBulkTopic(
        @Value("${KAFKA_BULK_TOPIC:queue-bulk}") String name,
        @Value("${KAFKA_TOPIC_PARTITIONS:3}") int partitions,
        @Value("${KAFKA_REPLICATION_FACTOR:1}") short replicas
    ) {
        return TopicBuilder.name(name).partitions(partitions).replicas(replicas).build();
    }
}
