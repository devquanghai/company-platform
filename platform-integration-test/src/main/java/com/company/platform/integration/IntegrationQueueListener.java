package com.company.platform.integration;

import com.company.platform.queue.api.consume.MessageContext;
import com.company.platform.queue.api.consume.MessageHandlingResult;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Predicate;

@Component
@Profile("integration-e2e")
public class IntegrationQueueListener {
    private final BlockingQueue<ReceivedEvent> events = new LinkedBlockingQueue<>();

    public MessageHandlingResult onKafka(
        IntegrationQueueEvent event, MessageContext context
    ) {
        events.add(new ReceivedEvent("kafka", event, context));
        return MessageHandlingResult.ACK;
    }

    public MessageHandlingResult onRabbit(
        IntegrationQueueEvent event, MessageContext context
    ) {
        events.add(new ReceivedEvent("rabbit", event, context));
        return MessageHandlingResult.ACK;
    }

    public ReceivedEvent await(
        Predicate<ReceivedEvent> predicate, Duration timeout
    ) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            ReceivedEvent candidate = events.poll(
                Math.min(Duration.ofMillis(200).toNanos(),
                    Math.max(1, deadline - System.nanoTime())),
                java.util.concurrent.TimeUnit.NANOSECONDS);
            if (candidate != null && predicate.test(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    public record ReceivedEvent(
        String provider,
        IntegrationQueueEvent event,
        MessageContext context
    ) {
    }
}
