package com.company.platform.cache.autoconfigure.properties;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.time.Duration;

@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ObservabilityProperties {
    boolean metricsEnabled = true;
    boolean healthEnabled = true;
    boolean eventsEnabled = true;
    boolean localHitObservationEnabled;
    String logCacheKey = "HASH";
    boolean logCacheValue;
    Duration healthTimeout = Duration.ofSeconds(1);
}
