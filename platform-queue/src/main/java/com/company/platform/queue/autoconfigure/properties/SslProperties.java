package com.company.platform.queue.autoconfigure.properties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SslProperties {
    private Boolean enabled;
    private String trustStoreLocation;
    private String trustStorePassword;
    private String keyStoreLocation;
    private String keyStorePassword;
    private String keyPassword;

    /** @deprecated RabbitMQ uses {@code rabbit.tls-enabled}; Kafka uses security protocol. */
    @Deprecated
    public boolean isEnabled() { return enabled == null || enabled; }
}
