package com.company.platform.exchange.autoconfigure.properties;

import lombok.Getter;
import lombok.Setter;
import java.time.Duration;

/** @deprecated Configure {@code resilience4j.ratelimiter.*}. */
@Deprecated
@Getter @Setter
public class RateLimiterProperties {
    private boolean enabled;
    private int limitForPeriod = 100;
    private Duration limitRefreshPeriod = Duration.ofSeconds(1);
    private Duration timeoutDuration = Duration.ZERO;
}
