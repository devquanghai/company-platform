package com.company.platform.queue.autoconfigure.properties;

import jakarta.validation.Valid;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.util.unit.DataSize;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "platform.queue")
public class PlatformQueueProperties {
    private boolean enabled = true;
    private String applicationName = "unknown";
    private String sourceApplication;
    private String environment;
    private boolean insecureTransportAllowed;
    private boolean allowNoop;
    @Valid private MessageProperties message = new MessageProperties();
    @Valid private TopologyProperties topology = new TopologyProperties();
    @Valid private QueueDefaultsProperties defaults = new QueueDefaultsProperties();
    @Valid private Map<String, BrokerProperties> brokers = new LinkedHashMap<>();
    @Valid private Map<String, DestinationProperties> destinations = new LinkedHashMap<>();
    @Valid private Map<String, SubscriptionProperties> subscriptions = new LinkedHashMap<>();
    @Valid private DeliveryProperties delivery = new DeliveryProperties();
    @Valid private ObservabilityProperties observability = new ObservabilityProperties();

    public String getApplicationName() {
        return "unknown".equals(applicationName)
            && sourceApplication != null && !sourceApplication.isBlank()
            ? sourceApplication : applicationName;
    }

    public boolean isInsecureTransportAllowed() {
        return insecureTransportAllowed
            || "local".equalsIgnoreCase(environment)
            || "test".equalsIgnoreCase(environment);
    }

    /** @deprecated use {@code application-name}. */
    @Deprecated
    public String getSourceApplication() {
        return sourceApplication;
    }

    /** @deprecated use {@code application-name}. */
    @Deprecated
    public void setSourceApplication(String value) {
        sourceApplication = value;
    }

    public MessageProperties getMessage() {
        if (defaults.getMaxPayloadBytes() != null
            && message.getMaxPayloadSize().equals(DataSize.ofMegabytes(1))) {
            message.setMaxPayloadSize(DataSize.ofBytes(defaults.getMaxPayloadBytes()));
        }
        if (defaults.getMaxEnvelopeBytes() != null
            && message.getMaxEnvelopeSize().equals(DataSize.ofMegabytes(2))) {
            message.setMaxEnvelopeSize(DataSize.ofBytes(defaults.getMaxEnvelopeBytes()));
        }
        if (message.getAllowedHeaders().isEmpty()
            && !defaults.getAllowedCustomHeaders().isEmpty()) {
            message.setAllowedHeaders(defaults.getAllowedCustomHeaders());
        }
        return message;
    }

    public TopologyProperties getTopology() {
        if (defaults.getTopologyDeclarationMode() != null
            && topology.getMode()
                == com.company.platform.queue.domain.policy.TopologyDeclarationMode.VALIDATE_ONLY) {
            topology.setMode(defaults.getTopologyDeclarationMode());
        }
        return topology;
    }

    /** @deprecated use {@code delivery}. */
    @Deprecated
    public DeliveryProperties getReliability() {
        return delivery;
    }
}
