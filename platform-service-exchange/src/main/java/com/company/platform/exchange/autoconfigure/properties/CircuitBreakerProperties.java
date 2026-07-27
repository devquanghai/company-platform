package com.company.platform.exchange.autoconfigure.properties;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.time.Duration;

import static lombok.AccessLevel.PRIVATE;

@Getter
@Setter
@FieldDefaults(level = PRIVATE)
public class CircuitBreakerProperties {

    boolean enabled = true;
    String slidingWindowType = "COUNT_BASED";
    int slidingWindowSize = 20;
    int minimumNumberOfCalls = 10;
    float failureRateThreshold = 50;
    float slowCallRateThreshold = 60;
    Duration slowCallDurationThreshold = Duration.ofSeconds(3);
    int permittedCallsInHalfOpenState = 5;
    Duration waitDurationInOpenState = Duration.ofSeconds(30);
}
