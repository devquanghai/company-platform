package com.company.platform.queue.autoconfigure.properties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SubscriptionProperties {
    private boolean enabled = true;
    private String destination;
    private KafkaSubscriptionProperties kafka = new KafkaSubscriptionProperties();
    private RabbitSubscriptionProperties rabbit = new RabbitSubscriptionProperties();
    private RetryProperties retry = new RetryProperties();
    private DeadLetterProperties deadLetter = new DeadLetterProperties();
    private boolean idempotencyEnabled;
}
