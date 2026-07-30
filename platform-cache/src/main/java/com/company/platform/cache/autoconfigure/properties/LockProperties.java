package com.company.platform.cache.autoconfigure.properties;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.time.Duration;

@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class LockProperties {
    boolean enabled;
    String provider = "REDISSON";
    Duration waitTime = Duration.ofSeconds(2);
    Duration leaseTime = Duration.ofSeconds(30);
    boolean watchdogEnabled = true;
    boolean fencingEnabled;
}
