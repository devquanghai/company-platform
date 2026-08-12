package com.company.platform.queue.consume.internal.adapter.rabbit;

import com.company.platform.core.time.TimeProvider;
import com.company.platform.queue.api.consume.MessageContext;
import com.company.platform.queue.api.consume.MessageHandlingResult;
import com.company.platform.queue.consume.internal.port.out.QueueListenerContainerAdapter;
import com.company.platform.queue.consume.internal.application.QueueListenerEndpoint;
import com.company.platform.queue.consume.internal.application.QueueMessageProcessor;
import com.company.platform.queue.autoconfigure.properties.DestinationProperties;
import com.company.platform.queue.autoconfigure.properties.PlatformQueueProperties;
import com.company.platform.queue.autoconfigure.properties.SubscriptionProperties;
import com.company.platform.queue.domain.model.QueueProviderType;
import com.company.platform.queue.domain.policy.RetryDecision;
import com.company.platform.queue.envelope.header.PlatformMessageHeaders;
import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.rabbit.listener.api.ChannelAwareMessageListener;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.SmartLifecycle;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

public final class NamedRabbitListenerContainerAdapter
    implements QueueListenerContainerAdapter, SmartLifecycle, DisposableBean {

    private final PlatformQueueProperties properties;
    private final QueueMessageProcessor processor;
    private final TimeProvider time;
    private final List<SimpleMessageListenerContainer> containers =
        new CopyOnWriteArrayList<>();
    private final SimpleRabbitListenerContainerFactory containerFactory;
    private final AtomicBoolean running = new AtomicBoolean();

    public NamedRabbitListenerContainerAdapter(
        PlatformQueueProperties properties,
        QueueMessageProcessor processor,
        TimeProvider time,
        SimpleRabbitListenerContainerFactory containerFactory
    ) {
        this.properties = properties;
        this.processor = processor;
        this.time = time;
        this.containerFactory = containerFactory;
    }

    @Override
    public QueueProviderType provider() {
        return QueueProviderType.RABBITMQ;
    }

    @Override
    public void register(
        QueueListenerEndpoint endpoint,
        SubscriptionProperties subscription,
        DestinationProperties destination
    ) {
        String brokerName = destination.getBroker();
        var rabbit = subscription.getRabbit();
        SimpleMessageListenerContainer container =
            containerFactory.createListenerContainer();
        container.setListenerId("platformQueueRabbit-" + endpoint.handlerId());
        container.setQueueNames(rabbit.getQueue());
        container.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        container.setDefaultRequeueRejected(false);
        container.setPrefetchCount(rabbit.getPrefetch());
        container.setConcurrentConsumers(rabbit.getConcurrency());
        container.setMaxConcurrentConsumers(rabbit.getMaxConcurrency());
        container.setExclusive(rabbit.isExclusive());
        container.setMissingQueuesFatal(true);
        container.setObservationEnabled(
            properties.getObservability().isTracingEnabled());
        container.setAutoStartup(false);
        container.setMessageListener((ChannelAwareMessageListener)
            (message, channel) -> onMessage(
                endpoint, subscription, destination, brokerName, message, channel));
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
        Message message,
        Channel channel
    ) throws java.io.IOException {
        var messageProperties = message.getMessageProperties();
        long deliveryTag = messageProperties.getDeliveryTag();
        int attempt = attempt(message);
        MessageContext context = new MessageContext(
            provider(), brokerName, endpoint.subscription(), subscription.getDestination(),
            messageProperties.getConsumerQueue(), messageProperties.getMessageId(),
            messageProperties.getCorrelationId(),
            stringHeader(message, PlatformMessageHeaders.CAUSATION_ID),
            safeHeaders(message), time.nowInstant(), attempt, null, null, null,
            messageProperties.getReceivedExchange(),
            messageProperties.getReceivedRoutingKey(),
            messageProperties.isRedelivered(),
            traceId(stringHeader(message, PlatformMessageHeaders.TRACEPARENT)));
        var outcome = processor.process(
            endpoint, subscription, destination, message.getBody(), context);
        if (outcome.result() == MessageHandlingResult.ACK
            || outcome.failureDecision() == RetryDecision.ACK_AND_SKIP) {
            channel.basicAck(deliveryTag, false);
            return;
        }
        boolean retry = outcome.result() == MessageHandlingResult.RETRY
            || outcome.failureDecision() == RetryDecision.RETRY_BLOCKING
            || outcome.failureDecision() == RetryDecision.RETRY_DELAYED;
        if (retry && attempt < subscription.getRetry().getMaxAttempts()) {
            channel.basicNack(deliveryTag, false, false);
            return;
        }
        channel.basicReject(deliveryTag, false);
    }

    @Override
    public void start() {
        if (running.compareAndSet(false, true)) {
            containers.forEach(SimpleMessageListenerContainer::start);
        }
    }

    @Override
    public void stop() {
        if (running.compareAndSet(true, false)) {
            containers.forEach(SimpleMessageListenerContainer::stop);
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
        containers.forEach(SimpleMessageListenerContainer::destroy);
        containers.clear();
    }

    private int attempt(Message message) {
        Object value = message.getMessageProperties()
            .getHeaders().get(PlatformMessageHeaders.DELIVERY_ATTEMPT);
        if (value instanceof Number number) {
            return Math.max(1, number.intValue());
        }
        if (value instanceof String text) {
            try {
                return Math.max(1, Integer.parseInt(text));
            } catch (NumberFormatException ignored) {
                return 1;
            }
        }
        return message.getMessageProperties().isRedelivered() ? 2 : 1;
    }

    private Map<String, String> safeHeaders(Message message) {
        Map<String, String> values = new LinkedHashMap<>();
        message.getMessageProperties().getHeaders().forEach((name, value) -> {
            if (PlatformMessageHeaders.RESERVED.contains(name) && value != null) {
                values.put(name, String.valueOf(value));
            }
        });
        return Map.copyOf(values);
    }

    private String stringHeader(Message message, String name) {
        Object value = message.getMessageProperties().getHeaders().get(name);
        return value == null ? null : String.valueOf(value);
    }

    private String traceId(String traceparent) {
        if (traceparent == null) {
            return null;
        }
        String[] parts = traceparent.split("-");
        return parts.length == 4 ? parts[1] : null;
    }
}
