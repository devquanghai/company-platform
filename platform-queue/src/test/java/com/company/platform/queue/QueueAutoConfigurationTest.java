package com.company.platform.queue;

import com.company.platform.core.json.JsonMapperHelper;
import com.company.platform.core.time.TimeProvider;
import com.company.platform.queue.api.publish.MessagePublisher;
import com.company.platform.queue.api.publish.PublishRequest;
import com.company.platform.queue.api.publish.PublishResult;
import com.company.platform.queue.autoconfigure.KafkaQueueAutoConfiguration;
import com.company.platform.queue.autoconfigure.PlatformQueueAutoConfiguration;
import com.company.platform.queue.autoconfigure.QueueListenerAutoConfiguration;
import com.company.platform.queue.autoconfigure.QueueObservabilityAutoConfiguration;
import com.company.platform.queue.autoconfigure.QueuePublisherAutoConfiguration;
import com.company.platform.queue.autoconfigure.QueueReliabilityAutoConfiguration;
import com.company.platform.queue.autoconfigure.RabbitQueueAutoConfiguration;
import com.company.platform.queue.autoconfigure.properties.PlatformQueueProperties;
import com.company.platform.queue.reliability.inbox.InboxStore;
import com.company.platform.queue.reliability.outbox.OutboxMessageStore;
import com.company.platform.queue.reliability.outbox.OutboxPollingLifecycle;
import com.company.platform.queue.reliability.outbox.OutboxPollingPublisher;
import com.company.platform.queue.reliability.outbox.TransactionalMessagePublisher;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CompletionStage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class QueueAutoConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(
            PlatformQueueAutoConfiguration.class,
            KafkaQueueAutoConfiguration.class,
            RabbitQueueAutoConfiguration.class,
            QueueReliabilityAutoConfiguration.class,
            QueuePublisherAutoConfiguration.class,
            QueueListenerAutoConfiguration.class,
            QueueObservabilityAutoConfiguration.class))
        .withUserConfiguration(Infrastructure.class);

    @Test
    void globalDisableCreatesNoQueueBeans() {
        runner.withPropertyValues("platform.queue.enabled=false")
            .run(context -> {
                assertThat(context).doesNotHaveBean(PlatformQueueProperties.class);
                assertThat(context).doesNotHaveBean(MessagePublisher.class);
            });
    }

    @Test
    void createsCommonFacadesWithoutConnectingBrokers() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(PlatformQueueProperties.class);
            assertThat(context).hasSingleBean(MessagePublisher.class);
            assertThat(context).hasBean("platformQueueHealthIndicator");
        });
    }

    @Test
    void customFacadeBacksOff() {
        runner.withUserConfiguration(CustomPublisher.class)
            .run(context -> assertThat(context.getBean(MessagePublisher.class))
                .isSameAs(context.getBean("customMessagePublisher")));
    }

    @Test
    void reliabilityFlagsFailWithoutDurableStores() {
        runner.withPropertyValues(
            "platform.queue.reliability.outbox-enabled=true")
            .run(context -> assertThat(context.getStartupFailure())
                .hasRootCauseMessage(
                    "platform.queue.reliability.outbox-enabled requires OutboxMessageStore"));
    }

    @Test
    void createsOutboxPollerAfterPublisherRegistryIsRegistered() {
        runner.withUserConfiguration(DurableStores.class)
            .withPropertyValues(
                "platform.queue.reliability.outbox-enabled=true",
                "platform.queue.reliability.inbox-enabled=true")
            .run(context -> {
                assertThat(context)
                    .hasSingleBean(TransactionalMessagePublisher.class);
                assertThat(context).hasSingleBean(OutboxPollingPublisher.class);
                assertThat(context).hasSingleBean(OutboxPollingLifecycle.class);
            });
    }

    @Test
    void importsAndMetadataArePackaged() throws IOException {
        ClassLoader loader = getClass().getClassLoader();
        String imports = new String(loader.getResourceAsStream(
            "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports")
            .readAllBytes(), StandardCharsets.UTF_8);
        String metadata = new String(loader.getResourceAsStream(
            "META-INF/additional-spring-configuration-metadata.json")
            .readAllBytes(), StandardCharsets.UTF_8);
        assertThat(imports).contains(
            PlatformQueueAutoConfiguration.class.getName(),
            KafkaQueueAutoConfiguration.class.getName(),
            RabbitQueueAutoConfiguration.class.getName(),
            QueueListenerAutoConfiguration.class.getName());
        assertThat(metadata).contains(
            "platform.queue.enabled", "Bật hoặc tắt", "\"defaultValue\": true");
    }

    @Configuration(proxyBeanMethods = false)
    static class Infrastructure {
        @Bean
        JsonMapperHelper jsonMapperHelper() {
            return new JsonMapperHelper(JsonMapper.builder().build());
        }

        @Bean
        TimeProvider timeProvider() {
            return new TimeProvider() {
                @Override public Instant nowInstant() { return Instant.EPOCH; }
                @Override public OffsetDateTime now() {
                    return OffsetDateTime.ofInstant(Instant.EPOCH, ZoneOffset.UTC);
                }
                @Override public OffsetDateTime now(ZoneId zoneId) {
                    return OffsetDateTime.ofInstant(Instant.EPOCH, zoneId);
                }
                @Override public ZoneId getDefaultZone() { return ZoneOffset.UTC; }
            };
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomPublisher {
        @Bean
        MessagePublisher customMessagePublisher() {
            return new MessagePublisher() {
                @Override public <T> PublishResult publish(String destination, T payload) {
                    return null;
                }
                @Override public <K, T> PublishResult publish(
                    String destination, K key, T payload) { return null; }
                @Override public <T> PublishResult publish(PublishRequest<T> request) {
                    return null;
                }
                @Override public <T> CompletionStage<PublishResult> publishAsync(
                    PublishRequest<T> request) { return null; }
                @Override public <T> List<PublishResult> publishBatch(
                    List<PublishRequest<T>> requests) { return List.of(); }
            };
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class DurableStores {
        @Bean
        OutboxMessageStore outboxMessageStore() {
            return mock(OutboxMessageStore.class);
        }

        @Bean
        InboxStore inboxStore() {
            return mock(InboxStore.class);
        }
    }
}
