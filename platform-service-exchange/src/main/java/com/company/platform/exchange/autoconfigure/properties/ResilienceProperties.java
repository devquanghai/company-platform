package com.company.platform.exchange.autoconfigure.properties;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import static lombok.AccessLevel.PRIVATE;

@Getter
@Setter
@FieldDefaults(level = PRIVATE)
public class ResilienceProperties {

    boolean enabled = true;
    RetryProperties retry = new RetryProperties();
    CircuitBreakerProperties circuitBreaker = new CircuitBreakerProperties();
    RateLimiterProperties rateLimiter = new RateLimiterProperties();
    BulkheadProperties bulkhead = new BulkheadProperties();
    TimeoutProperties timeout = new TimeoutProperties();
}
