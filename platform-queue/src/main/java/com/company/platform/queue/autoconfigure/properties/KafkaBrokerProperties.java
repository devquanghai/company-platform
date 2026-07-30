package com.company.platform.queue.autoconfigure.properties;

import lombok.Getter;
import lombok.Setter;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class KafkaBrokerProperties {
    private List<String> bootstrapServers = new ArrayList<>();
    private String clientIdPrefix = "platform-queue";
    private Duration requestTimeout = Duration.ofSeconds(10);
    private Duration deliveryTimeout = Duration.ofSeconds(30);
    private Duration maxBlock = Duration.ofSeconds(10);
    private String securityProtocol = "SSL";
    private String saslMechanism;
    private String username;
    private String password;
    private String acks = "all";
    private boolean enableIdempotence = true;
    private int retries = 10;
    private int maxInFlightRequestsPerConnection = 5;
    private boolean transactionEnabled;
    private String transactionalIdPrefix;
    private SslProperties ssl = new SslProperties();
}
