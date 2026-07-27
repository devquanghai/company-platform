package com.company.platform.exchange.autoconfigure.properties;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.time.Duration;

import static lombok.AccessLevel.PRIVATE;

@Getter
@Setter
@FieldDefaults(level = PRIVATE)
public class RateLimiterProperties {

    boolean enabled;
    int limitForPeriod = 100;
    Duration limitRefreshPeriod = Duration.ofSeconds(1);
    Duration timeoutDuration = Duration.ZERO;
}
