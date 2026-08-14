package com.company.platform.exchange.autoconfigure.properties;

import lombok.Getter;
import lombok.Setter;
import java.time.Duration;

/** @deprecated Configure native transport timeout or Resilience4j TimeLimiter. */
@Deprecated
@Getter @Setter
public class TimeoutProperties {
    private boolean enabled = true;
    private Duration duration = Duration.ofSeconds(10);
}
