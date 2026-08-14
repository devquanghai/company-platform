package com.company.platform.exchange.autoconfigure.properties;

import lombok.Getter;
import lombok.Setter;
import java.time.Duration;

/** @deprecated Configure {@code resilience4j.circuitbreaker.*}. */
@Deprecated
@Getter @Setter
public class CircuitBreakerProperties {
    private boolean enabled = true;
    private String slidingWindowType = "COUNT_BASED";
    private int slidingWindowSize = 20;
    private int minimumNumberOfCalls = 10;
    private float failureRateThreshold = 50;
    private float slowCallRateThreshold = 60;
    private Duration slowCallDurationThreshold = Duration.ofSeconds(3);
    private int permittedCallsInHalfOpenState = 5;
    private Duration waitDurationInOpenState = Duration.ofSeconds(30);
}
