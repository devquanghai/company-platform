package com.company.platform.exchange.domain.policy;

import java.time.Duration;

public final class RetryDecision {
    private final boolean retry;
    private final Duration delay;
    private final String reason;

    private RetryDecision(boolean retry, Duration delay, String reason) {
        this.retry = retry;
        this.delay = delay;
        this.reason = reason;
    }

    public static RetryDecision retry(Duration delay, String reason) {
        return new RetryDecision(true, delay, reason);
    }

    public static RetryDecision doNotRetry(String reason) {
        return new RetryDecision(false, Duration.ZERO, reason);
    }

    public boolean retry() { return retry; }
    public Duration delay() { return delay; }
    public String reason() { return reason; }
}
