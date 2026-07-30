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
public class BulkheadProperties {
    boolean enabled = true;
    @Min(1) int maxConcurrentCalls = 100;
    Duration maxWaitDuration = Duration.ZERO;
}
