package com.company.platform.exchange.autoconfigure.properties;

import com.company.platform.exchange.domain.model.ExchangeProtocol;
import jakarta.validation.Valid;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import static lombok.AccessLevel.PRIVATE;

@Getter
@Setter
@FieldDefaults(level = PRIVATE)
public class ClientProperties {

    boolean enabled = true;
    ExchangeProtocol protocol;

    @Valid
    HttpClientProperties http = new HttpClientProperties();

    @Valid
    GrpcClientProperties grpc = new GrpcClientProperties();

    @Valid
    SslProperties ssl = new SslProperties();

    @Valid
    ProxyProperties proxy = new ProxyProperties();

    @Valid
    LoggingProperties logging = new LoggingProperties();

    @Valid
    AuditProperties audit = new AuditProperties();

    @Valid
    ResilienceProperties resilience = new ResilienceProperties();
}
