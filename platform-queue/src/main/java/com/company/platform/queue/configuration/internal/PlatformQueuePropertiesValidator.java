package com.company.platform.queue.configuration.internal;

import com.company.platform.queue.autoconfigure.properties.BrokerProperties;
import com.company.platform.queue.autoconfigure.properties.DestinationProperties;
import com.company.platform.queue.autoconfigure.properties.PlatformQueueProperties;
import com.company.platform.queue.autoconfigure.properties.SubscriptionProperties;
import com.company.platform.queue.domain.exception.QueueConfigurationException;
import com.company.platform.queue.domain.model.QueueProviderType;
import com.company.platform.queue.domain.policy.KafkaRetryMode;
import com.company.platform.queue.api.kafka.KafkaConsumerMode;
import org.springframework.boot.amqp.autoconfigure.RabbitProperties;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;

import java.time.Duration;
import java.util.Map;
import java.util.regex.Pattern;

public final class PlatformQueuePropertiesValidator {
    private static final Pattern SAFE_NAME =
        Pattern.compile("[a-z0-9][a-z0-9._-]{0,126}");
    private final PlatformQueueProperties properties;
    private final boolean outboxStoreAvailable;
    private final boolean inboxStoreAvailable;
    private final boolean deferredKafkaStoreAvailable;
    private final RabbitProperties rabbitProperties;
    private final KafkaProperties kafkaProperties;

    public PlatformQueuePropertiesValidator(
        PlatformQueueProperties properties,
        boolean outboxStoreAvailable,
        boolean inboxStoreAvailable,
        boolean deferredKafkaStoreAvailable,
        RabbitProperties rabbitProperties,
        KafkaProperties kafkaProperties
    ) {
        this.properties = properties;
        this.outboxStoreAvailable = outboxStoreAvailable;
        this.inboxStoreAvailable = inboxStoreAvailable;
        this.deferredKafkaStoreAvailable = deferredKafkaStoreAvailable;
        this.rabbitProperties = rabbitProperties;
        this.kafkaProperties = kafkaProperties;
    }

    public void validate() {
        if (!properties.isEnabled()) {
            return;
        }
        validateLimits();
        properties.getBrokers().forEach(this::validateBroker);
        validateSingleNativeConnectionPerProvider();
        validateRabbitDeliveryGuarantees();
        validateKafkaDeliveryGuarantees();
        properties.getDestinations().forEach(this::validateDestination);
        properties.getSubscriptions().forEach(this::validateSubscription);
        if (properties.getDelivery().isOutboxEnabled() && !outboxStoreAvailable) {
            fail("platform.queue.delivery.outbox-enabled requires OutboxMessageStore");
        }
        if (properties.getDelivery().isInboxEnabled() && !inboxStoreAvailable) {
            fail("platform.queue.delivery.inbox-enabled requires InboxStore");
        }
    }

    private void validateKafkaDeliveryGuarantees() {
        boolean kafkaEnabled = properties.getBrokers().values().stream()
            .anyMatch(broker -> broker.isEnabled()
                && broker.getProvider() == QueueProviderType.KAFKA);
        if (!kafkaEnabled) {
            return;
        }
        if (kafkaProperties == null) {
            fail("Kafka logical broker requires Spring Boot Kafka auto-configuration");
        }
        Map<String, Object> producer = kafkaProperties.getProducer().buildProperties();
        Object acks = producer.get("acks");
        Object idempotence = producer.get("enable.idempotence");
        if (!("all".equals(String.valueOf(acks)) || "-1".equals(String.valueOf(acks)))
            || !Boolean.parseBoolean(String.valueOf(idempotence))) {
            fail("Kafka CONFIRMED semantics require native spring.kafka.producer.acks=all "
                + "and spring.kafka.producer.properties[enable.idempotence]=true");
        }
        Map<String, Object> consumer = kafkaProperties.getConsumer().buildProperties();
        if (Boolean.parseBoolean(String.valueOf(
            consumer.getOrDefault("enable.auto.commit", false)))) {
            fail("Kafka at-least-once consumption requires native "
                + "spring.kafka.consumer.enable-auto-commit=false");
        }
        if (kafkaProperties.getListener().getType()
            == KafkaProperties.Listener.Type.BATCH) {
            fail("platform queue record listeners require spring.kafka.listener.type=single");
        }
    }

    private void validateSingleNativeConnectionPerProvider() {
        for (QueueProviderType provider : java.util.List.of(
            QueueProviderType.KAFKA, QueueProviderType.RABBITMQ)) {
            long count = properties.getBrokers().values().stream()
                .filter(BrokerProperties::isEnabled)
                .filter(broker -> broker.getProvider() == provider)
                .count();
            if (count > 1) {
                fail("platform.queue supports one enabled logical broker per provider "
                    + "when using Boot's single native connection: " + provider);
            }
        }
    }

    private void validateRabbitDeliveryGuarantees() {
        boolean rabbitEnabled = properties.getBrokers().values().stream()
            .anyMatch(broker -> broker.isEnabled()
                && broker.getProvider() == QueueProviderType.RABBITMQ);
        if (!rabbitEnabled) {
            return;
        }
        if (rabbitProperties == null
            || rabbitProperties.getPublisherConfirmType()
                != CachingConnectionFactory.ConfirmType.CORRELATED
            || !rabbitProperties.isPublisherReturns()
            || !Boolean.TRUE.equals(rabbitProperties.getTemplate().getMandatory())) {
            fail("Rabbit CONFIRMED semantics require native properties "
                + "spring.rabbitmq.publisher-confirm-type=correlated, "
                + "spring.rabbitmq.publisher-returns=true and "
                + "spring.rabbitmq.template.mandatory=true");
        }
    }

    private void validateLimits() {
        com.company.platform.queue.configuration.internal.QueueMessageDefaults.limits(
            properties.getMessage());
        if (properties.getDelivery().getProcessingLockTimeout()
            .compareTo(Duration.ofSeconds(1)) < 0) {
            fail("platform.queue.delivery.processing-lock-timeout must be at least 1s");
        }
    }

    private void validateBroker(String name, BrokerProperties broker) {
        requireName("platform.queue.brokers", name);
        if (broker.getProvider() == null) {
            fail(path("brokers", name) + ".provider is required");
        }
        if (!broker.isEnabled()) {
            return;
        }
        if (broker.getProvider() == QueueProviderType.NOOP) {
            if (!properties.isAllowNoop()) {
                fail(path("brokers", name) + ".provider NOOP requires allow-noop=true");
            }
            return;
        }
        // Native broker connectivity, security and transactions are validated by
        // Spring Boot's spring.kafka.* and spring.rabbitmq.* auto-configuration.
    }

    private void validateDestination(String name, DestinationProperties destination) {
        requireName("platform.queue.destinations", name);
        if (!destination.isEnabled()) {
            return;
        }
        BrokerProperties broker = enabledBroker(
            path("destinations", name) + ".broker", destination.getBroker());
        if (broker.getProvider() == QueueProviderType.KAFKA
            && blank(destination.getKafka().getTopic())) {
            fail(path("destinations", name) + ".kafka.topic is required");
        }
        if (broker.getProvider() == QueueProviderType.RABBITMQ
            && blank(destination.getRabbit().getExchange())) {
            fail(path("destinations", name) + ".rabbit.exchange is required");
        }
        if (destination.getSerialization().getSchemaVersion() < 1) {
            fail(path("destinations", name) + ".serialization.schema-version must be at least 1");
        }
        positive(path("destinations", name) + ".send-timeout", destination.getSendTimeout());
        if (properties.getDelivery().isOutboxEnabled()
            && properties.getDelivery().getProcessingLockTimeout()
                .compareTo(destination.getSendTimeout()) <= 0) {
            fail("platform.queue.delivery.processing-lock-timeout must exceed "
                + path("destinations", name) + ".send-timeout");
        }
    }

    private void validateSubscription(String name, SubscriptionProperties subscription) {
        requireName("platform.queue.subscriptions", name);
        if (!subscription.isEnabled()) {
            return;
        }
        if (subscription.isIdempotencyEnabled() && !inboxStoreAvailable) {
            fail(path("subscriptions", name)
                + ".idempotency-enabled requires InboxStore");
        }
        DestinationProperties destination =
            properties.getDestinations().get(subscription.getDestination());
        if (destination == null) {
            fail(path("subscriptions", name) + ".destination references unknown destination");
        }
        if (!destination.isEnabled() || !destination.isConsumerEnabled()) {
            return;
        }
        BrokerProperties broker = enabledBroker(
            path("destinations", subscription.getDestination()) + ".broker",
            destination.getBroker());
        if (subscription.getRetry().getMaxAttempts() < 1) {
            fail(path("subscriptions", name) + ".retry.max-attempts must be at least 1");
        }
        if (broker.getProvider() == QueueProviderType.KAFKA) {
            if (blank(subscription.getKafka().getGroupId())) {
                fail(path("subscriptions", name) + ".kafka.group-id is required");
            }
            if (subscription.getRetry().getKafkaMode() == KafkaRetryMode.NON_BLOCKING
                || subscription.getRetry().getKafkaMode() == KafkaRetryMode.COMBINED) {
                fail(path("subscriptions", name)
                    + ".retry.kafka-mode currently supports only NONE or BLOCKING");
            }
            if (subscription.getRetry().isEnabled()
                && !subscription.getDeadLetter().isEnabled()) {
                fail(path("subscriptions", name)
                    + ".retry.enabled requires dead-letter.enabled=true");
            }
            if (subscription.getKafka().isStrictOrdering()
                && (subscription.getRetry().getKafkaMode() == KafkaRetryMode.NON_BLOCKING
                    || subscription.getRetry().getKafkaMode() == KafkaRetryMode.COMBINED
                    || !destination.getKafka().isKeyRequired())) {
                fail(path("subscriptions", name)
                    + " strict ordering requires key-required=true and blocking retry");
            }
            if (subscription.getKafka().getMode() != KafkaConsumerMode.REALTIME) {
                if (!deferredKafkaStoreAvailable) {
                    fail(path("subscriptions", name)
                        + ".kafka.mode BATCH/BULK requires DeferredKafkaMessageStore");
                }
                if (!subscription.isIdempotencyEnabled()) {
                    fail(path("subscriptions", name)
                        + ".kafka.mode BATCH/BULK requires idempotency-enabled=true");
                }
                if (subscription.getKafka().getMaxMessages() < 1) {
                    fail(path("subscriptions", name)
                        + ".kafka.max-messages must be at least 1");
                }
                positive(path("subscriptions", name) + ".kafka.max-wait",
                    subscription.getKafka().getMaxWait());
                positive(path("subscriptions", name) + ".kafka.deferred-poll-interval",
                    subscription.getKafka().getDeferredPollInterval());
            }
            if (subscription.getDeadLetter().isEnabled()) {
                String deadLetterName = subscription.getDeadLetter().getDestination();
                DestinationProperties deadLetter = properties.getDestinations().get(
                    deadLetterName);
                if (blank(deadLetterName) || deadLetter == null
                    || !deadLetter.isEnabled() || !deadLetter.isProducerEnabled()) {
                    fail(path("subscriptions", name)
                        + ".dead-letter.destination must reference an enabled producer destination");
                }
                if (!java.util.Objects.equals(
                    destination.getBroker(), deadLetter.getBroker())) {
                    fail(path("subscriptions", name)
                        + ".dead-letter.destination must use the source Kafka broker");
                }
                if (deadLetter.getKafka().getPartitions()
                    < destination.getKafka().getPartitions()) {
                    fail(path("subscriptions", name)
                        + ".dead-letter.destination must preserve partition count");
                }
            }
        } else if (broker.getProvider() == QueueProviderType.RABBITMQ) {
            if (blank(subscription.getRabbit().getQueue())) {
                fail(path("subscriptions", name) + ".rabbit.queue is required");
            }
            if (subscription.getRabbit().getPrefetch() < 1
                || subscription.getRabbit().getConcurrency() < 1
                || subscription.getRabbit().getMaxConcurrency()
                    < subscription.getRabbit().getConcurrency()) {
                fail(path("subscriptions", name) + ".rabbit concurrency/prefetch is invalid");
            }
        }
    }

    private BrokerProperties enabledBroker(String path, String name) {
        if (blank(name)) {
            fail(path + " is required");
        }
        BrokerProperties broker = properties.getBrokers().get(name);
        if (broker == null || !broker.isEnabled()) {
            fail(path + " references unknown/disabled broker");
        }
        return broker;
    }

    private void requireName(String path, String name) {
        if (name == null || !SAFE_NAME.matcher(name).matches()) {
            fail(path + " contains an invalid name");
        }
    }

    private void positive(String path, Duration duration) {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            fail(path + " must be positive");
        }
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private String path(String collection, String name) {
        return "platform.queue." + collection + "." + name;
    }

    private void fail(String message) {
        throw new QueueConfigurationException(message);
    }
}
