package com.company.platform.cache.autoconfigure.properties;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.time.Duration;

@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MultiLevelProperties {
    boolean enabled;
    String l1Store;
    String l2Store;
    Duration l1Ttl = Duration.ofMinutes(5);
    boolean populateL1OnL2Hit = true;
}
