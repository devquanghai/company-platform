package com.company.platform.queue.autoconfigure.properties;

import jakarta.validation.Valid;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.LinkedHashMap;
import java.util.Map;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "platform.queue")
public class PlatformQueueProperties {
    private boolean enabled = true;
    private boolean annotationsEnabled = true;
    private String sourceApplication = "unknown";
    private String environment = "local";
    private boolean allowNoop;
    @Valid private QueueDefaultsProperties defaults = new QueueDefaultsProperties();
    @Valid private Map<String, BrokerProperties> brokers = new LinkedHashMap<>();
    @Valid private Map<String, DestinationProperties> destinations = new LinkedHashMap<>();
    @Valid private Map<String, SubscriptionProperties> subscriptions = new LinkedHashMap<>();
    @Valid private ReliabilityProperties reliability = new ReliabilityProperties();
    @Valid private ObservabilityProperties observability = new ObservabilityProperties();
}
