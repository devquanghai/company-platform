package com.company.platform.queue.autoconfigure.properties;

import com.company.platform.queue.domain.policy.KafkaRetryMode;
import com.company.platform.queue.domain.policy.RabbitRetryMode;
import lombok.Getter;
import lombok.Setter;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class RetryProperties {
    private boolean enabled = true;
    private int maxAttempts = 3;
    private Duration initialInterval = Duration.ofSeconds(1);
    private double multiplier = 2.0;
    private Duration maxInterval = Duration.ofSeconds(30);
    private Duration maxElapsedTime = Duration.ofMinutes(5);
    private KafkaRetryMode kafkaMode = KafkaRetryMode.BLOCKING;
    private RabbitRetryMode rabbitMode = RabbitRetryMode.BLOCKING;
    private List<String> retryableExceptions = new ArrayList<>();
    private List<String> notRetryableExceptions = new ArrayList<>();
}
