package com.company.platform.queue.autoconfigure.properties;

import lombok.Getter;
import lombok.Setter;

import java.time.Duration;

@Getter
@Setter
public class ReliabilityProperties {
    private boolean outboxEnabled;
    private boolean inboxEnabled;
    private int outboxBatchSize = 100;
    private Duration outboxPollInterval = Duration.ofSeconds(1);
    private Duration lockTimeout = Duration.ofSeconds(30);
    private int maxAttempts = 20;
}
