package com.company.platform.exchange.autoconfigure.properties;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.time.Duration;

import static lombok.AccessLevel.PRIVATE;

@Getter
@Setter
@FieldDefaults(level = PRIVATE)
public class BulkheadProperties {

    boolean enabled;
    int maxConcurrentCalls = 50;
    Duration maxWaitDuration = Duration.ZERO;
}
