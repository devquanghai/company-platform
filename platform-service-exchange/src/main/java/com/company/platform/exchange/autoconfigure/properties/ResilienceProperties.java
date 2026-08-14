package com.company.platform.exchange.autoconfigure.properties;

import lombok.Getter;
import lombok.Setter;

/** @deprecated Configure native {@code resilience4j.*} instances. */
@Deprecated
@Getter @Setter
public class ResilienceProperties {
    private boolean enabled = true;
    private RetryProperties retry = new RetryProperties();
    private CircuitBreakerProperties circuitBreaker = new CircuitBreakerProperties();
    private RateLimiterProperties rateLimiter = new RateLimiterProperties();
    private BulkheadProperties bulkhead = new BulkheadProperties();
    private TimeoutProperties timeout = new TimeoutProperties();
}
