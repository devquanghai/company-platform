package com.company.platform.exchange.autoconfigure.properties;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.time.Duration;

import static lombok.AccessLevel.PRIVATE;

@Getter
@Setter
@FieldDefaults(level = PRIVATE)
public class HttpPoolProperties {

    int maxTotal = 200;
    int maxPerRoute = 50;
    Duration validateAfterInactivity = Duration.ofSeconds(5);
    Duration timeToLive = Duration.ofMinutes(5);
    Duration evictIdleConnectionsAfter = Duration.ofSeconds(60);
}
