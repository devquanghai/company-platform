package com.company.platform.exchange.autoconfigure.properties;

import lombok.Getter;
import lombok.Setter;
import java.time.Duration;

/** @deprecated Configure the Boot-selected HTTP client. */
@Deprecated
@Getter @Setter
public class HttpPoolProperties {
    private int maxTotal = 200;
    private int maxPerRoute = 50;
    private Duration validateAfterInactivity = Duration.ofSeconds(5);
    private Duration timeToLive = Duration.ofMinutes(5);
    private Duration evictIdleConnectionsAfter = Duration.ofSeconds(60);
}
