package com.company.platform.exchange.autoconfigure.properties;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import org.springframework.util.unit.DataSize;

import java.time.Duration;

import static lombok.AccessLevel.PRIVATE;

@Getter
@Setter
@FieldDefaults(level = PRIVATE)
public class GrpcClientProperties {

    String address;
    GrpcNegotiationType negotiationType = GrpcNegotiationType.TLS;
    Duration defaultDeadline = Duration.ofSeconds(5);
    DataSize maxInboundMessageSize = DataSize.ofMegabytes(8);
    Duration keepAliveTime = Duration.ofSeconds(30);
    Duration keepAliveTimeout = Duration.ofSeconds(10);
    boolean keepAliveWithoutCalls;
    String authorityOverride;
}
