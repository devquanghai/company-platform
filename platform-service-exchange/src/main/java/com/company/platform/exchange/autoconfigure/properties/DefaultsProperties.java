package com.company.platform.exchange.autoconfigure.properties;

import lombok.Getter;
import lombok.Setter;
import java.time.Duration;

/** @deprecated Use native Boot and Resilience4j defaults. */
@Deprecated
@Getter @Setter
public class DefaultsProperties {
    private Duration connectTimeout = Duration.ofSeconds(3);
    private Duration requestTimeout = Duration.ofSeconds(10);
    private LoggingProperties logging = new LoggingProperties();
    private AuditProperties audit = new AuditProperties();
    private ResilienceProperties resilience = new ResilienceProperties();
}
