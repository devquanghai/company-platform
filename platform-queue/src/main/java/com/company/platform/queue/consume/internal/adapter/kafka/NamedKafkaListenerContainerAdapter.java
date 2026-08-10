package com.company.platform.queue.consume.internal.adapter.kafka;

import com.company.platform.core.time.TimeProvider;
import com.company.platform.queue.api.consume.MessageContext;
import com.company.platform.queue.api.consume.MessageHandlingResult;
import com.company.platform.queue.api.kafka.DeferredKafkaBatch;
import com.company.platform.queue.api.kafka.DeferredKafkaMessage;
import com.company.platform.queue.api.kafka.DeferredKafkaMessageStore;
import com.company.platform.queue.api.kafka.KafkaConsumerMode;
import com.company.platform.queue.consume.internal.port.out.QueueListenerContainerAdapter;
import com.company.platform.queue.consume.internal.application.QueueListenerEndpoint;
import com.company.platform.queue.consume.internal.application.QueueMessageProcessor;
import com.company.platform.queue.autoconfigure.properties.DestinationProperties;
import com.company.platform.queue.autoconfigure.properties.PlatformQueueProperties;
import com.company.platform.queue.autoconfigure.properties.SubscriptionProperties;
import com.company.platform.queue.domain.model.QueueProviderType;
import com.company.platform.queue.domain.policy.RetryDecision;
import com.company.platform.queue.consume.internal.port.out.KafkaDeadLetterPublisher;
import com.company.platform.queue.envelope.header.PlatformMessageHeaders;
import com.company.platform.queue.configuration.internal.adapter.kafka.KafkaSecurityConfiguration;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.SmartLifecycle;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.AbstractMessageListenerContainer;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.util.backoff.ExponentialBackOff;

import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class NamedKafkaListenerContainerAdapter
    implements QueueListenerContainerAdapter, SmartLifecycle, DisposableBean {

    private final PlatformQueueProperties properties;
    private final QueueMessageProcessor processor;
    private final TimeProvider time;
    private final DeferredKafkaMessageStore deferredStore;
    private final KafkaDeadLetterPublisher deadLetterPublisher;
    private final List<AbstractMessageListenerContainer<String, byte[]>> containers =
        new CopyOnWriteArrayList<>();
    private final AtomicBoolean running = new AtomicBoolean();
    private final List<DeferredRegistration> deferredRegistrations =
        new CopyOnWriteArrayList<>();
    private volatile ScheduledExecutorService deferredWorker;
    private volatile ScheduledExecutorService claimHeartbeatWorker;

    public NamedKafkaListenerContainerAdapter(
        PlatformQueueProperties properties,
        QueueMessageProcessor processor,
        TimeProvider time,
        DeferredKafkaMessageStore deferredStore,
        KafkaDeadLetterPublisher deadLetterPublisher
    ) {
        this.properties = properties;
        this.processor = processor;
        this.time = time;
        this.deferredStore = deferredStore;
        this.deadLetterPublisher = deadLetterPublisher;
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
        containerProperties.setDeliveryAttemptHeader(true);
        containerProperties.setMissingTopicsFatal(true);
        if (subscription.getKafka().getMode() == KafkaConsumerMode.REALTIME) {
            containerProperties.setMessageListener(
                (org.springframework.kafka.listener.AcknowledgingMessageListener<String, byte[]>)
                    (record, acknowledgment) -> onMessage(
                        endpoint, subscription, destination, brokerName,
                        record, acknowledgment));
        } else {
            if (deferredStore == null) {
                throw new IllegalStateException(
                    "BATCH/BULK Kafka subscription requires DeferredKafkaMessageStore");
            }
            deferredRegistrations.add(new DeferredRegistration(
                endpoint, subscription, destination, brokerName));
            containerProperties.setMessageListener(
                (org.springframework.kafka.listener.AcknowledgingMessageListener<String, byte[]>)
                    (record, acknowledgment) -> stageDeferred(
                        endpoint, subscription, destination, brokerName,
                        record, acknowledgment));
        }
        var container = new ConcurrentMessageListenerContainer<String, byte[]>(
            factory, containerProperties);
        container.setCommonErrorHandler(kafkaErrorHandler(
            subscription, brokerName));
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
        int attempt = deliveryAttempt(record);
        MessageContext context = context(
            endpoint, subscription, destination, brokerName, record, attempt);
        var outcome = processor.process(
            endpoint, subscription, destination, record.value(), context);
        acknowledgeOutcome(endpoint, subscription, record, acknowledgment, outcome);
    }

    private void stageDeferred(
        QueueListenerEndpoint endpoint,
        SubscriptionProperties subscription,
        DestinationProperties destination,
        String brokerName,
        ConsumerRecord<String, byte[]> record,
        Acknowledgment acknowledgment
    ) {
        if (subscription.getKafka().isStrictOrdering()
            && (record.key() == null || record.key().isBlank())) {
            throw new IllegalArgumentException(
                "strict-ordering Kafka message requires a non-blank key");
        }
        int attempt = deliveryAttempt(record);
        deferredStore.stage(new DeferredKafkaMessage(
            endpoint.subscription(), record.key(), record.value(),
            context(endpoint, subscription, destination, brokerName, record, attempt)));
        acknowledgment.acknowledge();
    }

    private MessageContext context(
        QueueListenerEndpoint endpoint,
        SubscriptionProperties subscription,
        DestinationProperties destination,
        String brokerName,
        ConsumerRecord<String, byte[]> record,
        int attempt
    ) {
        return new MessageContext(
            provider(), brokerName, endpoint.subscription(), subscription.getDestination(),
            record.topic(), textHeader(record, PlatformMessageHeaders.MESSAGE_ID),
            textHeader(record, PlatformMessageHeaders.CORRELATION_ID),
            textHeader(record, PlatformMessageHeaders.CAUSATION_ID),
            safeHeaders(record), time.nowInstant(), attempt,
            record.partition(), record.offset(),
            subscription.getKafka().getGroupId(), null, null, attempt > 1,
            traceId(textHeader(record, PlatformMessageHeaders.TRACEPARENT)));
    }

    private void acknowledgeOutcome(
        QueueListenerEndpoint endpoint,
        SubscriptionProperties subscription,
        ConsumerRecord<String, byte[]> record,
        Acknowledgment acknowledgment,
        com.company.platform.queue.consume.internal.application.ListenerInvocationResult outcome
    ) {
        if (outcome.result() == MessageHandlingResult.ACK) {
            acknowledgment.acknowledge();
            return;
        }
        if (outcome.retryAt() != null) {
            Duration wait = Duration.between(time.nowInstant(), outcome.retryAt());
            acknowledgment.nack(wait.isNegative() || wait.isZero()
                ? Duration.ofMillis(100) : wait);
            return;
        }
        boolean retry = outcome.result() == MessageHandlingResult.RETRY
            || outcome.failureDecision() == RetryDecision.RETRY_BLOCKING
            || outcome.failureDecision() == RetryDecision.RETRY_DELAYED;
        if (retry) {
            throw new KafkaRetryRequiredException(endpoint.subscription());
        }
        if (outcome.result() == MessageHandlingResult.REJECT
            || outcome.failureDecision() == RetryDecision.REJECT) {
            if (!subscription.getDeadLetter().isEnabled()) {
                acknowledgment.acknowledge();
                return;
            }
        }
        throw new KafkaDeadLetterRequiredException(
            endpoint.subscription(), record.topic(), record.partition(), record.offset());
    }

    private DefaultErrorHandler kafkaErrorHandler(
        SubscriptionProperties subscription,
        String brokerName
    ) {
        ExponentialBackOff backOff = new ExponentialBackOff(
            subscription.getRetry().getInitialInterval().toMillis(),
            subscription.getRetry().getMultiplier());
        backOff.setMaxInterval(subscription.getRetry().getMaxInterval().toMillis());
        backOff.setMaxElapsedTime(subscription.getRetry().getMaxElapsedTime().toMillis());
        backOff.setMaxAttempts(subscription.getRetry().isEnabled()
            ? Math.max(0, subscription.getRetry().getMaxAttempts() - 1L) : 0);
        DefaultErrorHandler handler = new DefaultErrorHandler(
            (record, failure) -> recoverDeadLetter(
                subscription, brokerName, record), backOff);
        handler.addNotRetryableExceptions(KafkaDeadLetterRequiredException.class);
        handler.setCommitRecovered(true);
        handler.setAckAfterHandle(true);
        return handler;
    }

    private void recoverDeadLetter(
        SubscriptionProperties subscription,
        String brokerName,
        ConsumerRecord<?, ?> record
    ) {
        if (!subscription.getDeadLetter().isEnabled()) {
            return;
        }
        if (deadLetterPublisher == null) {
            throw new IllegalStateException(
                "Kafka DLT requires KafkaDeadLetterPublisher");
        }
        DestinationProperties deadLetter = properties.getDestinations().get(
            subscription.getDeadLetter().getDestination());
        if (deadLetter == null) {
            throw new IllegalStateException("Kafka dead-letter destination is missing");
        }
        String topic = deadLetter.getKafka().getTopic();
        int partition = record.partition();
        @SuppressWarnings("unchecked")
        ConsumerRecord<String, byte[]> typed = (ConsumerRecord<String, byte[]>) record;
        RecordHeaders headers = new RecordHeaders(typed.headers().toArray());
        headers.add(KafkaHeaders.DLT_ORIGINAL_TOPIC,
            typed.topic().getBytes(StandardCharsets.UTF_8));
        headers.add(KafkaHeaders.DLT_ORIGINAL_PARTITION,
            ByteBuffer.allocate(Integer.BYTES).putInt(typed.partition()).array());
        headers.add(KafkaHeaders.DLT_ORIGINAL_OFFSET,
            ByteBuffer.allocate(Long.BYTES).putLong(typed.offset()).array());
        headers.add("x-platform-failure-code",
            "QUEUE.KAFKA_LISTENER_EXHAUSTED".getBytes(StandardCharsets.UTF_8));
        deadLetterPublisher.publishDeadLetter(
            brokerName, topic, partition, typed.key(), typed.value(),
            headers, deadLetter.getSendTimeout());
    }

    @Override
    public void start() {
        if (running.compareAndSet(false, true)) {
            containers.forEach(AbstractMessageListenerContainer::start);
            startDeferredWorker();
        }
    }

    @Override
    public void stop() {
        if (running.compareAndSet(true, false)) {
            containers.forEach(AbstractMessageListenerContainer::stop);
            shutdownDeferredWorkers();
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
        deferredRegistrations.clear();
    }

    private void startDeferredWorker() {
        if (deferredRegistrations.isEmpty() || deferredWorker != null) {
            return;
        }
        deferredWorker = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "platform-queue-kafka-deferred");
            thread.setDaemon(true);
            return thread;
        });
        claimHeartbeatWorker = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "platform-queue-kafka-claim-heartbeat");
            thread.setDaemon(true);
            return thread;
        });
        long intervalMillis = deferredRegistrations.stream()
            .map(registration -> registration.subscription().getKafka()
                .getDeferredPollInterval())
            .mapToLong(java.time.Duration::toMillis)
            .min()
            .orElse(1_000L);
        deferredWorker.scheduleWithFixedDelay(
            this::processDeferredSafely, 0, intervalMillis, TimeUnit.MILLISECONDS);
    }

    private void processDeferredSafely() {
        if (!running.get()) {
            return;
        }
        deferredRegistrations.forEach(registration -> {
            try {
                processDeferred(registration);
            } catch (RuntimeException ignored) {
                // Durable claim expires; health/metrics adapters report store failures.
            }
        });
    }

    private void processDeferred(DeferredRegistration registration) {
        var kafka = registration.subscription().getKafka();
        deferredStore.claimReady(
            registration.endpoint().subscription(), kafka.getMaxMessages(),
            kafka.getMaxWait(), properties.getDelivery().getProcessingLockTimeout(),
            time.nowInstant()).ifPresent(batch -> processDeferredBatch(registration, batch));
    }

    private void processDeferredBatch(
        DeferredRegistration registration, DeferredKafkaBatch batch
    ) {
        validateDeferredBatch(registration, batch);
        Duration lockTimeout = properties.getDelivery().getProcessingLockTimeout();
        AtomicReference<RuntimeException> renewalFailure = new AtomicReference<>();
        ScheduledFuture<?> heartbeat = claimHeartbeatWorker.scheduleWithFixedDelay(() -> {
            try {
                deferredStore.renewClaim(
                    batch.claimId(), batch.ownerId(), batch.fencingToken(), lockTimeout);
            } catch (RuntimeException failure) {
                renewalFailure.compareAndSet(null, failure);
            }
        }, Math.max(100, lockTimeout.toMillis() / 3),
            Math.max(100, lockTimeout.toMillis() / 3), TimeUnit.MILLISECONDS);
        String failureCode = null;
        java.time.Instant contentionRetryAt = null;
        int completed = 0;
        try {
            for (DeferredKafkaMessage message : batch.messages()) {
                if (renewalFailure.get() != null) {
                    throw renewalFailure.get();
                }
                var outcome = processor.process(
                    registration.endpoint(), registration.subscription(),
                    registration.destination(), message.body(), message.context());
                if (outcome.result() != MessageHandlingResult.ACK) {
                    if (outcome.retryAt() != null) {
                        contentionRetryAt = outcome.retryAt();
                    }
                    failureCode = outcome.failureDecision() == null
                        ? "QUEUE.DEFERRED_HANDLER_NOT_ACKNOWLEDGED"
                        : "QUEUE.DEFERRED_" + outcome.failureDecision().name();
                    break;
                }
                completed++;
            }
        } finally {
            heartbeat.cancel(false);
        }
        if (failureCode == null) {
            deferredStore.markCompleted(
                batch.claimId(), batch.ownerId(), batch.fencingToken());
            return;
        }
        if (contentionRetryAt != null) {
            deferredStore.releaseContended(
                batch.claimId(), batch.ownerId(), batch.fencingToken(),
                contentionRetryAt);
            return;
        }
        if (batch.attempt() >= registration.subscription().getRetry().getMaxAttempts()) {
            publishDeferredDeadLetter(
                registration, batch.messages().get(completed), batch.attempt(), failureCode);
            deferredStore.markDeadLetter(
                batch.claimId(), batch.ownerId(), batch.fencingToken(),
                completed, failureCode);
            return;
        }
        deferredStore.release(
            batch.claimId(), batch.ownerId(), batch.fencingToken(),
            time.nowInstant().plus(backoff(registration.subscription(), batch.attempt())),
            failureCode);
    }

    private void publishDeferredDeadLetter(
        DeferredRegistration registration,
        DeferredKafkaMessage message,
        int attempt,
        String failureCode
    ) {
        if (!registration.subscription().getDeadLetter().isEnabled()) {
            return;
        }
        if (deadLetterPublisher == null) {
            throw new IllegalStateException("Kafka DLT requires KafkaDeadLetterPublisher");
        }
        DestinationProperties deadLetter = properties.getDestinations().get(
            registration.subscription().getDeadLetter().getDestination());
        RecordHeaders headers = new RecordHeaders();
        message.context().headers().forEach((name, value) ->
            headers.add(name, value.getBytes(StandardCharsets.UTF_8)));
        headers.add(KafkaHeaders.DLT_ORIGINAL_TOPIC,
            message.context().physicalDestination().getBytes(StandardCharsets.UTF_8));
        headers.add(KafkaHeaders.DLT_ORIGINAL_PARTITION,
            ByteBuffer.allocate(Integer.BYTES).putInt(message.context().partition()).array());
        headers.add(KafkaHeaders.DLT_ORIGINAL_OFFSET,
            ByteBuffer.allocate(Long.BYTES).putLong(message.context().offset()).array());
        headers.add(PlatformMessageHeaders.DELIVERY_ATTEMPT,
            Integer.toString(attempt).getBytes(StandardCharsets.UTF_8));
        headers.add("x-platform-failure-code",
            failureCode.getBytes(StandardCharsets.UTF_8));
        deadLetterPublisher.publishDeadLetter(
            registration.brokerName(), deadLetter.getKafka().getTopic(),
            message.context().partition(), message.messageKey(), message.body(),
            headers, deadLetter.getSendTimeout());
    }

    private void validateDeferredBatch(
        DeferredRegistration registration, DeferredKafkaBatch batch
    ) {
        if (batch.attempt() < 1
            || batch.messages().size() > registration.subscription().getKafka()
                .getMaxMessages()) {
            throw new IllegalStateException("invalid deferred Kafka batch bounds");
        }
        DeferredKafkaMessage first = batch.messages().getFirst();
        String topic = first.context().physicalDestination();
        Integer partition = first.context().partition();
        Long previousOffset = null;
        for (DeferredKafkaMessage message : batch.messages()) {
            if (!registration.endpoint().subscription().equals(message.subscription())
                || !java.util.Objects.equals(topic, message.context().physicalDestination())
                || !java.util.Objects.equals(partition, message.context().partition())
                || message.context().offset() == null
                || (previousOffset != null && message.context().offset() != previousOffset + 1)) {
                throw new IllegalStateException(
                    "deferred Kafka batch must contain contiguous ordered partition offsets");
            }
            previousOffset = message.context().offset();
        }
    }

    private void shutdownDeferredWorkers() {
        ScheduledExecutorService worker = deferredWorker;
        deferredWorker = null;
        if (worker != null) {
            worker.shutdown();
            try {
                long timeout = Math.min(30_000,
                    properties.getDelivery().getProcessingLockTimeout().toMillis());
                if (!worker.awaitTermination(timeout, TimeUnit.MILLISECONDS)) {
                    worker.shutdownNow();
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                worker.shutdownNow();
            }
        }
        ScheduledExecutorService heartbeat = claimHeartbeatWorker;
        claimHeartbeatWorker = null;
        if (heartbeat != null) {
            heartbeat.shutdownNow();
        }
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
            subscription.getKafka().isReadCommitted()
                ? "read_committed" : "read_uncommitted");
        if (subscription.getKafka().getMode() != KafkaConsumerMode.REALTIME) {
            config.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG,
                Math.min(subscription.getKafka().getMaxMessages(), 10_000));
        }
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

    private int deliveryAttempt(ConsumerRecord<String, byte[]> record) {
        Header springAttempt = record.headers().lastHeader(KafkaHeaders.DELIVERY_ATTEMPT);
        if (springAttempt != null && springAttempt.value() != null
            && springAttempt.value().length == Integer.BYTES) {
            return ByteBuffer.wrap(springAttempt.value()).getInt();
        }
        return integerHeader(record, PlatformMessageHeaders.DELIVERY_ATTEMPT, 1);
    }

    private String traceId(String traceparent) {
        if (traceparent == null) {
            return null;
        }
        String[] parts = traceparent.split("-");
        return parts.length == 4 ? parts[1] : null;
    }

    private record DeferredRegistration(
        QueueListenerEndpoint endpoint,
        SubscriptionProperties subscription,
        DestinationProperties destination,
        String brokerName
    ) {
    }
}
