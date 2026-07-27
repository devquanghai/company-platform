package com.company.platform.exchange.autoconfigure.properties;

import jakarta.validation.Valid;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import static lombok.AccessLevel.PRIVATE;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "platform.service-exchange")
@FieldDefaults(level = PRIVATE)
public class ServiceExchangeProperties {

    boolean enabled = true;
    boolean eagerInitialization;
    boolean allowInsecureSsl;
    String environment = "local";
    String sourceApplication = "unknown";
    Duration shutdownTimeout = Duration.ofSeconds(10);

    @Valid
    DefaultsProperties defaults = new DefaultsProperties();

    @Valid
    Map<String, ClientProperties> clients = new LinkedHashMap<>();
}
