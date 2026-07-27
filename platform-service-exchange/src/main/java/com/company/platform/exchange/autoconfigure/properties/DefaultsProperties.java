package com.company.platform.exchange.autoconfigure.properties;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.time.Duration;

import static lombok.AccessLevel.PRIVATE;

@Getter
@Setter
@FieldDefaults(level = PRIVATE)
public class DefaultsProperties {

    Duration connectTimeout = Duration.ofSeconds(3);
    Duration requestTimeout = Duration.ofSeconds(10);
    LoggingProperties logging = new LoggingProperties();
    AuditProperties audit = new AuditProperties();
    ResilienceProperties resilience = new ResilienceProperties();
}
