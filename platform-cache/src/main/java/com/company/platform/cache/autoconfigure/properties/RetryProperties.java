package com.company.platform.cache.autoconfigure.properties;

import jakarta.validation.constraints.Min;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RetryProperties {
    boolean enabled = true;
    @Min(1) int maxAttempts = 2;
    Duration waitDuration = Duration.ofMillis(100);
    boolean exponentialBackoffEnabled = true;
    double multiplier = 2.0D;
    Duration maxWaitDuration = Duration.ofMillis(500);
    List<String> retryExceptions = new ArrayList<>();
    List<String> ignoreExceptions = new ArrayList<>();
}
