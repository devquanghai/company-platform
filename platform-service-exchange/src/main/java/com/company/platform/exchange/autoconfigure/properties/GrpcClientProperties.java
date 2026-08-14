package com.company.platform.exchange.autoconfigure.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.util.unit.DataSize;
import java.time.Duration;

/** @deprecated Configure {@code spring.grpc.client.channels.*}. */
@Deprecated
@Getter @Setter
public class GrpcClientProperties {
    private String address;
    private GrpcNegotiationType negotiationType = GrpcNegotiationType.TLS;
    private Duration defaultDeadline = Duration.ofSeconds(5);
    private DataSize maxInboundMessageSize = DataSize.ofMegabytes(8);
    private Duration keepAliveTime = Duration.ofSeconds(30);
    private Duration keepAliveTimeout = Duration.ofSeconds(10);
    private boolean keepAliveWithoutCalls;
    private String authorityOverride;
}
