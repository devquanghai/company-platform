package com.company.platform.exchange.autoconfigure.properties;

import com.company.platform.exchange.api.client.ServiceExchangeClientType;
import com.company.platform.exchange.domain.model.ExchangeProtocol;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import static lombok.AccessLevel.PRIVATE;

@Getter
@Setter
@FieldDefaults(level = PRIVATE)
public class ClientProperties {

    /** Enables this named client. */
    boolean enabled = true;
    /** Selects the blocking, reactive, or native gRPC reference adapter. */
    ServiceExchangeClientType type = ServiceExchangeClientType.WEBCLIENT;
    /** HTTP(S) origin for WEBCLIENT and RESTCLIENT. */
    String baseUrl;
    /** Native spring.grpc.client channel name for GRPC. */
    String grpcChannel;
    /** Native Resilience4j instance name; defaults to the client name. */
    String resilienceInstance;
    /** Spring SSL Bundle name for HTTP clients. */
    String sslBundle;
    /** Enables the named native Resilience4j pipeline. */
    boolean resilienceEnabled = true;
    /** Enables the platform client-identity observation. */
    boolean observabilityEnabled = true;
    /** Compatibility safe-logging behavior, not HTTP transport configuration. */
    LoggingProperties logging = new LoggingProperties();
    /** Compatibility outbound-audit behavior. */
    AuditProperties audit = new AuditProperties();
}
