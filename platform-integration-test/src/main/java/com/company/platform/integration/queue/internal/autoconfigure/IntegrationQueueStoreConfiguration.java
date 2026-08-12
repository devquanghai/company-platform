package com.company.platform.integration.queue.internal.autoconfigure;

import com.company.platform.integration.queue.internal.adapter.store.InMemoryDeferredKafkaMessageStore;
import com.company.platform.integration.queue.internal.adapter.store.InMemoryInboxStore;
import com.company.platform.queue.api.kafka.DeferredKafkaMessageStore;
import com.company.platform.queue.reliability.inbox.InboxStore;
import com.company.platform.core.time.TimeProvider;
import com.company.platform.integration.queue.internal.application.QueueMessageProbe;
import com.company.platform.integration.queue.internal.application.QueuePublishService;
import com.company.platform.integration.queue.internal.port.out.QueueEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.List;
import java.nio.file.Path;

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
    InboxStore integrationQueueInboxStore() {
        return new InMemoryInboxStore();
    }

    @Bean
    DeferredKafkaMessageStore integrationDeferredKafkaMessageStore() {
        return new InMemoryDeferredKafkaMessageStore(Path.of(
            System.getProperty("java.io.tmpdir"),
            "platform-integration-queue", "deferred-wal-v1.bin"));
    }
}
