package com.company.platform.queue.adapter.rabbit.topology;

import com.company.platform.queue.application.port.out.QueueTopologyManager;
import com.company.platform.queue.application.port.out.TopologyProvisionResult;
import com.company.platform.queue.application.port.out.TopologyValidationResult;
import com.company.platform.queue.autoconfigure.properties.PlatformQueueProperties;
import com.company.platform.queue.autoconfigure.properties.RabbitSubscriptionProperties;
import com.company.platform.queue.domain.model.QueueProviderType;
import com.company.platform.queue.adapter.rabbit.configuration.RabbitConnectionFactoryConfigurer;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RabbitQueueTopologyManager implements QueueTopologyManager {
    private static final String INVALID = "QUEUE.RABBIT_TOPOLOGY_INVALID";
    private static final String PROVISION_FAILED =
        "QUEUE.RABBIT_TOPOLOGY_PROVISION_FAILED";
    private final PlatformQueueProperties properties;

    public RabbitQueueTopologyManager(PlatformQueueProperties properties) {
        this.properties = properties;
    }

    @Override
    public TopologyValidationResult validate() {
        List<String> errors = new ArrayList<>();
        subscriptionsByBroker().forEach((brokerName, subscriptions) -> {
            CachingConnectionFactory factory = connectionFactory(brokerName);
            try {
                RabbitAdmin admin = new RabbitAdmin(factory);
                subscriptions.forEach(entry -> {
                    if (admin.getQueueInfo(entry.getValue().getRabbit().getQueue()) == null) {
                        errors.add(INVALID + ":" + entry.getKey());
                    }
                });
            } catch (Exception exception) {
                errors.add(INVALID + ":" + brokerName);
            } finally {
                factory.destroy();
            }
        });
        return new TopologyValidationResult(errors.isEmpty(), errors);
    }

    @Override
    public TopologyProvisionResult provision() {
        int[] counts = new int[2];
        List<String> errors = new ArrayList<>();
        subscriptionsByBroker().forEach((brokerName, subscriptions) -> {
            CachingConnectionFactory factory = connectionFactory(brokerName);
            try {
                RabbitAdmin admin = new RabbitAdmin(factory);
                for (var entry : subscriptions) {
                    var subscription = entry.getValue();
                    var destination = properties.getDestinations().get(
                        subscription.getDestination());
                    Exchange exchange = exchange(destination);
                    Queue queue = queue(subscription.getRabbit());
                    boolean existed = admin.getQueueInfo(queue.getName()) != null;
                    admin.declareExchange(exchange);
                    admin.declareQueue(queue);
                    admin.declareBinding(new Binding(
                        queue.getName(), Binding.DestinationType.QUEUE,
                        exchange.getName(), destination.getRabbit().getRoutingKey(),
                        Map.of()));
                    if (existed) {
                        counts[1]++;
                    } else {
                        counts[0]++;
                    }
                }
            } catch (Exception exception) {
                errors.add(PROVISION_FAILED + ":" + brokerName);
            } finally {
                factory.destroy();
            }
        });
        return new TopologyProvisionResult(counts[0], counts[1], errors);
    }

    private Exchange exchange(
        com.company.platform.queue.autoconfigure.properties.DestinationProperties
            destination
    ) {
        var rabbit = destination.getRabbit();
        ExchangeBuilder builder = switch (rabbit.getExchangeType()) {
            case DIRECT -> ExchangeBuilder.directExchange(rabbit.getExchange());
            case TOPIC -> ExchangeBuilder.topicExchange(rabbit.getExchange());
            case FANOUT -> ExchangeBuilder.fanoutExchange(rabbit.getExchange());
            case HEADERS -> ExchangeBuilder.headersExchange(rabbit.getExchange());
        };
        builder.durable(rabbit.isDurable())
            .withArguments(rabbit.getExchangeArguments());
        if (rabbit.isAutoDelete()) {
            builder.autoDelete();
        }
        if (rabbit.isInternal()) {
            builder.internal();
        }
        if (rabbit.getAlternateExchange() != null) {
            builder.alternate(rabbit.getAlternateExchange());
        }
        return builder.build();
    }

    private Queue queue(RabbitSubscriptionProperties rabbit) {
        QueueBuilder builder = rabbit.isDurable()
            ? QueueBuilder.durable(rabbit.getQueue())
            : QueueBuilder.nonDurable(rabbit.getQueue());
        builder.withArguments(rabbit.getQueueArguments());
        switch (rabbit.getQueueType()) {
            case CLASSIC -> builder.classic();
            case QUORUM -> builder.quorum();
            case STREAM -> builder.stream();
        }
        if (rabbit.isExclusive()) {
            builder.exclusive();
        }
        if (rabbit.isAutoDelete()) {
            builder.autoDelete();
        }
        if (rabbit.getDeadLetterExchange() != null) {
            builder.deadLetterExchange(rabbit.getDeadLetterExchange());
        }
        if (rabbit.getDeadLetterRoutingKey() != null) {
            builder.deadLetterRoutingKey(rabbit.getDeadLetterRoutingKey());
        }
        if (rabbit.getMessageTtl() != null) {
            builder.ttl(Math.toIntExact(rabbit.getMessageTtl().toMillis()));
        }
        if (rabbit.getQueueTtl() != null) {
            builder.expires(Math.toIntExact(rabbit.getQueueTtl().toMillis()));
        }
        if (rabbit.getMaxLength() != null) {
            builder.maxLength(rabbit.getMaxLength());
        }
        if (rabbit.getDeliveryLimit() != null) {
            builder.deliveryLimit(rabbit.getDeliveryLimit());
        }
        if (rabbit.isSingleActiveConsumer()) {
            builder.singleActiveConsumer();
        }
        return builder.build();
    }

    private Map<String, List<Map.Entry<String,
        com.company.platform.queue.autoconfigure.properties.SubscriptionProperties>>>
        subscriptionsByBroker() {
        Map<String, List<Map.Entry<String,
            com.company.platform.queue.autoconfigure.properties.SubscriptionProperties>>>
            result = new LinkedHashMap<>();
        properties.getSubscriptions().entrySet().stream()
            .filter(entry -> entry.getValue().isEnabled())
            .filter(entry -> {
                var destination = properties.getDestinations().get(
                    entry.getValue().getDestination());
                if (destination == null || !destination.isEnabled()) {
                    return false;
                }
                var broker = properties.getBrokers().get(destination.getBroker());
                return broker != null && broker.isEnabled()
                    && broker.getProvider() == QueueProviderType.RABBITMQ;
            })
            .forEach(entry -> {
                String broker = properties.getDestinations().get(
                    entry.getValue().getDestination()).getBroker();
                result.computeIfAbsent(
                    broker, ignored -> new ArrayList<>()).add(entry);
            });
        return result;
    }

    private CachingConnectionFactory connectionFactory(String brokerName) {
        var broker = properties.getBrokers().get(brokerName).getRabbit();
        return RabbitConnectionFactoryConfigurer.create(broker, false);
    }
}
