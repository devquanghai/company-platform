package com.company.platform.cache.autoconfigure.properties;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.time.Duration;

@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TopologyRefreshProperties {
    boolean enabled = true;
    boolean adaptive = true;
    Duration period = Duration.ofSeconds(30);
}
