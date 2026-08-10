package com.company.platform.queue.autoconfigure.properties;

import lombok.Getter;
import lombok.Setter;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class RabbitBrokerProperties {
    private List<String> addresses = new ArrayList<>();
    private String virtualHost = "/";
    private String username;
    private String password;
    private Duration connectionTimeout = Duration.ofSeconds(5);
    private Duration requestedHeartbeat = Duration.ofSeconds(30);
    private Boolean tlsEnabled;
    private SslProperties ssl = new SslProperties();

    public boolean isTlsEnabled() {
        return tlsEnabled == null ? ssl.isEnabled() : tlsEnabled;
    }
}
