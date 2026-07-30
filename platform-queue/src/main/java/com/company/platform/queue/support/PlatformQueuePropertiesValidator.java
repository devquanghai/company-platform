package com.company.platform.queue.support;

import com.company.platform.queue.autoconfigure.properties.BrokerProperties;
import com.company.platform.queue.autoconfigure.properties.DestinationProperties;
import com.company.platform.queue.autoconfigure.properties.PlatformQueueProperties;
import com.company.platform.queue.autoconfigure.properties.SubscriptionProperties;
import com.company.platform.queue.domain.exception.QueueConfigurationException;
import com.company.platform.queue.domain.model.QueueProviderType;
import com.company.platform.queue.domain.policy.KafkaRetryMode;
import com.company.platform.queue.envelope.validation.MessageLimits;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public final class PlatformQueuePropertiesValidator {
    private static final Pattern SAFE_NAME =
        Pattern.compile("[a-z0-9][a-z0-9._-]{0,126}");
    private static final Set<String> SECURE_KAFKA =
        Set.of("SSL", "SASL_SSL");
    private final PlatformQueueProperties properties;
    private final boolean outboxStoreAvailable;
    private final boolean inboxStoreAvailable;

    public PlatformQueuePropertiesValidator(
        PlatformQueueProperties properties,
        boolean outboxStoreAvailable,
        boolean inboxStoreAvailable
    ) {
        this.properties = properties;
        this.outboxStoreAvailable = outboxStoreAvailable;
        this.inboxStoreAvailable = inboxStoreAvailable;
    }

    public void validate() {
        if (!properties.isEnabled()) {
            return;
        }
        validateLimits();
        properties.getBrokers().forEach(this::validateBroker);
        properties.getDestinations().forEach(this::validateDestination);
        properties.getSubscriptions().forEach(this::validateSubscription);
        if (properties.getReliability().isOutboxEnabled() && !outboxStoreAvailable) {
            fail("platform.queue.reliability.outbox-enabled requires OutboxMessageStore");
        }
        if (properties.getReliability().isInboxEnabled() && !inboxStoreAvailable) {
            fail("platform.queue.reliability.inbox-enabled requires InboxStore");
        }
    }

    private void validateLimits() {
        var defaults = properties.getDefaults();
        new MessageLimits(
            defaults.getMaxHeaders(), defaults.getMaxHeaderBytes(),
            defaults.getMaxTotalHeaderBytes(), defaults.getMaxPayloadBytes(),
            defaults.getMaxEnvelopeBytes());
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
        if (broker.getProvider() == QueueProviderType.KAFKA) {
            if (broker.getKafka().getBootstrapServers().isEmpty()) {
                fail(path("brokers", name) + ".kafka.bootstrap-servers is required");
            }
            if (!"all".equalsIgnoreCase(broker.getKafka().getAcks())
                || !broker.getKafka().isEnableIdempotence()
                || broker.getKafka().getRetries() < 1
                || broker.getKafka().getMaxInFlightRequestsPerConnection() > 5) {
                fail(path("brokers", name) + ".kafka producer safety invariants are invalid");
            }
            if (production() && !SECURE_KAFKA.contains(
                broker.getKafka().getSecurityProtocol().toUpperCase())) {
                fail(path("brokers", name) + ".kafka.security-protocol is insecure");
            }
            if (production() && !broker.getKafka().getSsl().isVerifyHostname()) {
                fail(path("brokers", name)
                    + ".kafka.ssl.verify-hostname must be true");
            }
            if (broker.getKafka().getSecurityProtocol().startsWith("SASL")
                && (blank(broker.getKafka().getSaslMechanism())
                    || blank(broker.getKafka().getUsername())
                    || blank(broker.getKafka().getPassword()))) {
                fail(path("brokers", name)
                    + ".kafka SASL mechanism/credentials are required");
            }
            if (broker.getKafka().isTransactionEnabled()
                && blank(broker.getKafka().getTransactionalIdPrefix())) {
                fail(path("brokers", name) + ".kafka.transactional-id-prefix is required");
            }
        } else {
            if (broker.getRabbit().getAddresses().isEmpty()) {
                fail(path("brokers", name) + ".rabbit.addresses is required");
            }
            if (production() && !broker.getRabbit().getSsl().isEnabled()) {
                fail(path("brokers", name) + ".rabbit.ssl.enabled must be true");
            }
            if (!broker.getRabbit().getSsl().isVerifyHostname()) {
                fail(path("brokers", name) + ".rabbit.ssl.verify-hostname must be true");
            }
            if (!broker.getRabbit().isCorrelatedConfirms()
                || !broker.getRabbit().isReturnsEnabled()
                || !broker.getRabbit().isMandatory()) {
                fail(path("brokers", name)
                    + ".rabbit confirms, returns and mandatory publish are required");
            }
        }
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
    }

    private void validateSubscription(String name, SubscriptionProperties subscription) {
        requireName("platform.queue.subscriptions", name);
        if (!subscription.isEnabled()) {
            return;
        }
        DestinationProperties destination =
            properties.getDestinations().get(subscription.getDestination());
        if (destination == null || !destination.isEnabled()) {
            fail(path("subscriptions", name) + ".destination references unknown/disabled destination");
        }
        if (!destination.isConsumerEnabled()) {
            fail(path("subscriptions", name)
                + ".destination references consumer-disabled destination");
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
            if (subscription.getKafka().isTransactionEnabled()
                && (subscription.getRetry().getKafkaMode() == KafkaRetryMode.NON_BLOCKING
                    || subscription.getRetry().getKafkaMode() == KafkaRetryMode.COMBINED)) {
                fail(path("subscriptions", name)
                    + " cannot combine Kafka transaction and non-blocking retry");
            }
            if (subscription.getKafka().isStrictOrdering()
                && subscription.getRetry().getKafkaMode() == KafkaRetryMode.NON_BLOCKING) {
                fail(path("subscriptions", name)
                    + " strict ordering forbids non-blocking retry");
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

    private boolean production() {
        return "prod".equalsIgnoreCase(properties.getEnvironment())
            || "production".equalsIgnoreCase(properties.getEnvironment());
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
