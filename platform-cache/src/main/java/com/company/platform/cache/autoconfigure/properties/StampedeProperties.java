package com.company.platform.cache.autoconfigure.properties;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.time.Duration;

@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class StampedeProperties {
    boolean enabled = true;
    String mode = "SINGLE_FLIGHT";
    Duration waitTimeout = Duration.ofSeconds(2);
    int maximumInflight = 10_000;
}
