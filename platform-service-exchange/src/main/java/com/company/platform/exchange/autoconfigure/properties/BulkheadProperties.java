package com.company.platform.exchange.autoconfigure.properties;

import lombok.Getter;
import lombok.Setter;
import java.time.Duration;

/** @deprecated Configure {@code resilience4j.bulkhead.*}. */
@Deprecated
@Getter @Setter
public class BulkheadProperties {
    private boolean enabled;
    private int maxConcurrentCalls = 50;
    private Duration maxWaitDuration = Duration.ZERO;
}
