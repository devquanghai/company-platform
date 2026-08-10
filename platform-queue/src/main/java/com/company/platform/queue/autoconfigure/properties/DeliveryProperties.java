package com.company.platform.queue.autoconfigure.properties;

import lombok.Getter;
import lombok.Setter;

import java.time.Duration;

@Getter
@Setter
public class DeliveryProperties {
    private boolean outboxEnabled;
    private boolean inboxEnabled;
    private int outboxBatchSize = 100;
    private Duration outboxPollInterval = Duration.ofSeconds(1);
    private Duration processingLockTimeout = Duration.ofSeconds(30);
    private int outboxMaxAttempts = 20;

    /** @deprecated use {@code processing-lock-timeout}. */
    @Deprecated
    public Duration getLockTimeout() { return processingLockTimeout; }

    /** @deprecated use {@code processing-lock-timeout}. */
    @Deprecated
    public void setLockTimeout(Duration value) { processingLockTimeout = value; }

    /** @deprecated use {@code outbox-max-attempts}. */
    @Deprecated
    public int getMaxAttempts() { return outboxMaxAttempts; }

    /** @deprecated use {@code outbox-max-attempts}. */
    @Deprecated
    public void setMaxAttempts(int value) { outboxMaxAttempts = value; }
}
