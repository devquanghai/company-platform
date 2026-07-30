package com.company.platform.queue;

import com.company.platform.queue.autoconfigure.properties.BrokerProperties;
import com.company.platform.queue.autoconfigure.properties.DestinationProperties;
import com.company.platform.queue.autoconfigure.properties.PlatformQueueProperties;
import com.company.platform.queue.autoconfigure.properties.SubscriptionProperties;
import com.company.platform.queue.domain.exception.QueueConfigurationException;
import com.company.platform.queue.domain.model.QueueProviderType;
import com.company.platform.queue.domain.policy.KafkaRetryMode;
import com.company.platform.queue.support.PlatformQueuePropertiesValidator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlatformQueuePropertiesValidatorTest {

    @Test
    void acceptsValidKafkaAndRabbitConfiguration() {
        PlatformQueueProperties properties = new PlatformQueueProperties();
        properties.getBrokers().put("kafka-main", kafka());
        properties.getBrokers().put("rabbit-main", rabbit());
        DestinationProperties kafkaDestination = new DestinationProperties();
        kafkaDestination.setBroker("kafka-main");
        kafkaDestination.getKafka().setTopic("events.v1");
        properties.getDestinations().put("events", kafkaDestination);
        SubscriptionProperties kafkaSubscription = new SubscriptionProperties();
        kafkaSubscription.setDestination("events");
        kafkaSubscription.getKafka().setGroupId("projection");
        properties.getSubscriptions().put("events-projection", kafkaSubscription);
        DestinationProperties rabbitDestination = new DestinationProperties();
        rabbitDestination.setBroker("rabbit-main");
        rabbitDestination.getRabbit().setExchange("commands");
        properties.getDestinations().put("commands", rabbitDestination);
        SubscriptionProperties rabbitSubscription = new SubscriptionProperties();
        rabbitSubscription.setDestination("commands");
        rabbitSubscription.getRabbit().setQueue("commands.execute");
        properties.getSubscriptions().put("command-handler", rabbitSubscription);

        new PlatformQueuePropertiesValidator(properties, false, false).validate();
    }

    @Test
    void failsUnsafeAndInconsistentConfiguration() {
        PlatformQueueProperties properties = new PlatformQueueProperties();
        BrokerProperties kafka = kafka();
        kafka.getKafka().setAcks("1");
        properties.getBrokers().put("kafka-main", kafka);
        assertFailure(properties, "safety invariants");

        properties = new PlatformQueueProperties();
        properties.setEnvironment("production");
        BrokerProperties rabbit = rabbit();
        rabbit.getRabbit().getSsl().setEnabled(false);
        properties.getBrokers().put("rabbit-main", rabbit);
        assertFailure(properties, "ssl.enabled");

        properties = new PlatformQueueProperties();
        properties.getReliability().setOutboxEnabled(true);
        assertFailure(properties, "OutboxMessageStore");

        properties = new PlatformQueueProperties();
        properties.getReliability().setInboxEnabled(true);
        assertFailure(properties, "InboxStore");
    }

    @Test
    void rejectsUnknownReferencesAndTransactionRetryConflict() {
        PlatformQueueProperties properties = new PlatformQueueProperties();
        DestinationProperties missing = new DestinationProperties();
        missing.setBroker("missing");
        properties.getDestinations().put("events", missing);
        assertFailure(properties, "unknown/disabled broker");

        properties = new PlatformQueueProperties();
        properties.getBrokers().put("kafka-main", kafka());
        DestinationProperties destination = new DestinationProperties();
        destination.setBroker("kafka-main");
        destination.getKafka().setTopic("events");
        properties.getDestinations().put("events", destination);
        SubscriptionProperties subscription = new SubscriptionProperties();
        subscription.setDestination("events");
        subscription.getKafka().setGroupId("group");
        subscription.getKafka().setTransactionEnabled(true);
        subscription.getRetry().setKafkaMode(KafkaRetryMode.NON_BLOCKING);
        properties.getSubscriptions().put("listener", subscription);
        assertFailure(properties, "cannot combine");
    }

    private BrokerProperties kafka() {
        BrokerProperties broker = new BrokerProperties();
        broker.setProvider(QueueProviderType.KAFKA);
        broker.getKafka().setBootstrapServers(List.of("localhost:9092"));
        return broker;
    }

    private BrokerProperties rabbit() {
        BrokerProperties broker = new BrokerProperties();
        broker.setProvider(QueueProviderType.RABBITMQ);
        broker.getRabbit().setAddresses(List.of("localhost:5671"));
        return broker;
    }

    private void assertFailure(PlatformQueueProperties properties, String message) {
        assertThatThrownBy(() ->
            new PlatformQueuePropertiesValidator(properties, false, false).validate())
            .isInstanceOf(QueueConfigurationException.class)
            .hasMessageContaining(message);
    }
}
