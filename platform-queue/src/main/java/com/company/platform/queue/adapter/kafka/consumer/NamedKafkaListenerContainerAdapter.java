package com.company.platform.queue.adapter.kafka.consumer;

import com.company.platform.core.time.TimeProvider;
import com.company.platform.queue.api.consume.MessageContext;
import com.company.platform.queue.api.consume.MessageHandlingResult;
import com.company.platform.queue.application.port.in.QueueListenerContainerAdapter;
import com.company.platform.queue.application.registry.QueueListenerEndpoint;
import com.company.platform.queue.application.service.QueueMessageProcessor;
import com.company.platform.queue.autoconfigure.properties.DestinationProperties;
import com.company.platform.queue.autoconfigure.properties.PlatformQueueProperties;
import com.company.platform.queue.autoconfigure.properties.SubscriptionProperties;
import com.company.platform.queue.domain.model.QueueProviderType;
import com.company.platform.queue.domain.policy.RetryDecision;
import com.company.platform.queue.envelope.header.PlatformMessageHeaders;
import com.company.platform.queue.adapter.kafka.configuration.KafkaSecurityConfiguration;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.SmartLifecycle;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.AbstractMessageListenerContainer;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.Acknowledgment;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

public final class NamedKafkaListenerContainerAdapter
    implements QueueListenerContainerAdapter, SmartLifecycle, DisposableBean {

    private final PlatformQueueProperties properties;
    private final QueueMessageProcessor processor;
    private final TimeProvider time;
    private final List<AbstractMessageListenerContainer<String, byte[]>> containers =
        new CopyOnWriteArrayList<>();
    private final AtomicBoolean running = new AtomicBoolean();

    public NamedKafkaListenerContainerAdapter(
        PlatformQueueProperties properties,
        QueueMessageProcessor processor,
        TimeProvider time
    ) {
        this.properties = properties;
        this.processor = processor;
        this.time = time;
    }

    @Override
    public QueueProviderType provider() {
        return QueueProviderType.KAFKA;
    }

    @Override
    public void register(
        QueueListenerEndpoint endpoint,
        SubscriptionProperties subscription,
        DestinationProperties destination
    ) {
        String brokerName = destination.getBroker();
        var broker = properties.getBrokers().get(brokerName).getKafka();
        Map<String, Object> config = consumerConfig(
            brokerName, broker, subscription);
        var factory = new DefaultKafkaConsumerFactory<String, byte[]>(config);
        ContainerProperties containerProperties =
            new ContainerProperties(destination.getKafka().getTopic());
        containerProperties.setGroupId(subscription.getKafka().getGroupId());
        containerProperties.setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        containerProperties.setObservationEnabled(
            properties.getObservability().isTracingEnabled());
        containerProperties.setMissingTopicsFatal(true);
        containerProperties.setMessageListener(
            (org.springframework.kafka.listener.AcknowledgingMessageListener<String, byte[]>)
                (record, acknowledgment) -> onMessage(
                    endpoint, subscription, destination, brokerName,
                    record, acknowledgment));
        var container = new ConcurrentMessageListenerContainer<String, byte[]>(
            factory, containerProperties);
        container.setBeanName("platformQueueKafka-" + endpoint.handlerId());
        container.setConcurrency(subscription.getKafka().getConcurrency());
        container.setAutoStartup(false);
        containers.add(container);
        if (running.get()) {
            container.start();
        }
    }

    void onMessage(
        QueueListenerEndpoint endpoint,
        SubscriptionProperties subscription,
        DestinationProperties destination,
        String brokerName,
        ConsumerRecord<String, byte[]> record,
        Acknowledgment acknowledgment
    ) {
        int attempt = integerHeader(
            record, PlatformMessageHeaders.DELIVERY_ATTEMPT, 1);
        MessageContext context = new MessageContext(
            provider(), brokerName, endpoint.subscription(), endpoint.subscription(),
            record.topic(), textHeader(record, PlatformMessageHeaders.MESSAGE_ID),
            textHeader(record, PlatformMessageHeaders.CORRELATION_ID),
            textHeader(record, PlatformMessageHeaders.CAUSATION_ID),
            safeHeaders(record), time.nowInstant(), attempt,
            record.partition(), record.offset(),
            subscription.getKafka().getGroupId(), null, null, attempt > 1,
            traceId(textHeader(record, PlatformMessageHeaders.TRACEPARENT)));
        var outcome = processor.process(
            endpoint, subscription, destination, record.value(), context);
        if (outcome.result() == MessageHandlingResult.ACK) {
            acknowledgment.acknowledge();
            return;
        }
        boolean retry = outcome.result() == MessageHandlingResult.RETRY
            || outcome.failureDecision() == RetryDecision.RETRY_BLOCKING
            || outcome.failureDecision() == RetryDecision.RETRY_DELAYED;
        if (retry && attempt < subscription.getRetry().getMaxAttempts()) {
            acknowledgment.nack(backoff(subscription, attempt));
            return;
        }
        if (outcome.result() == MessageHandlingResult.REJECT
            || outcome.failureDecision() == RetryDecision.REJECT) {
            acknowledgment.acknowledge();
            return;
        }
        throw new KafkaDeadLetterRequiredException(
            endpoint.subscription(), record.topic(), record.partition(), record.offset());
    }

    @Override
    public void start() {
        if (running.compareAndSet(false, true)) {
            containers.forEach(AbstractMessageListenerContainer::start);
        }
    }

    @Override
    public void stop() {
        if (running.compareAndSet(true, false)) {
            containers.forEach(AbstractMessageListenerContainer::stop);
        }
    }

    @Override
    public void stop(Runnable callback) {
        stop();
        callback.run();
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public void destroy() {
        stop();
        containers.clear();
    }

    private Map<String, Object> consumerConfig(
        String brokerName,
        com.company.platform.queue.autoconfigure.properties.KafkaBrokerProperties broker,
        SubscriptionProperties subscription
    ) {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, broker.getBootstrapServers());
        config.put(ConsumerConfig.CLIENT_ID_CONFIG,
            broker.getClientIdPrefix() + "-consumer-" + brokerName);
        config.put(ConsumerConfig.GROUP_ID_CONFIG, subscription.getKafka().getGroupId());
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
        config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
            subscription.getKafka().getAutoOffsetReset());
        config.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG,
            subscription.getKafka().isTransactionEnabled()
                ? "read_committed" : "read_uncommitted");
        KafkaSecurityConfiguration.apply(config, broker);
        return config;
    }

    private java.time.Duration backoff(
        SubscriptionProperties subscription, int attempt
    ) {
        double factor = Math.pow(
            subscription.getRetry().getMultiplier(), Math.max(0, attempt - 1));
        long calculated = (long) (subscription.getRetry()
            .getInitialInterval().toMillis() * factor);
        return java.time.Duration.ofMillis(Math.min(
            calculated, subscription.getRetry().getMaxInterval().toMillis()));
    }

    private Map<String, String> safeHeaders(ConsumerRecord<String, byte[]> record) {
        Map<String, String> result = new LinkedHashMap<>();
        record.headers().forEach(header -> {
            if (PlatformMessageHeaders.RESERVED.contains(header.key())
                && header.value() != null) {
                result.put(header.key(), new String(
                    header.value(), StandardCharsets.UTF_8));
            }
        });
        return Map.copyOf(result);
    }

    private String textHeader(ConsumerRecord<String, byte[]> record, String name) {
        Header value = record.headers().lastHeader(name);
        return value == null || value.value() == null ? null
            : new String(value.value(), StandardCharsets.UTF_8);
    }

    private int integerHeader(
        ConsumerRecord<String, byte[]> record, String name, int fallback
    ) {
        String value = textHeader(record, name);
        try {
            return value == null ? fallback : Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private String traceId(String traceparent) {
        if (traceparent == null) {
            return null;
        }
        String[] parts = traceparent.split("-");
        return parts.length == 4 ? parts[1] : null;
    }
}
