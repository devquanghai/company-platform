package com.company.platform.cache.autoconfigure.properties;

import jakarta.validation.constraints.Min;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.time.Duration;

@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CircuitBreakerProperties {
    boolean enabled = true;
    @Min(1) int slidingWindowSize = 20;
    @Min(1) int minimumNumberOfCalls = 10;
    float failureRateThreshold = 50.0F;
    Duration waitDurationInOpenState = Duration.ofSeconds(10);
    @Min(1) int permittedCallsInHalfOpenState = 3;
}
