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

    /** @deprecated Use {@link #getType()}. */
    @Deprecated(forRemoval = true)
    public ExchangeProtocol getProtocol() {
        return type == ServiceExchangeClientType.GRPC
            ? ExchangeProtocol.GRPC : ExchangeProtocol.HTTP;
    }

    /** @deprecated Use {@link #setType(ServiceExchangeClientType)}. */
    @Deprecated(forRemoval = true)
    public void setProtocol(ExchangeProtocol protocol) {
        type = protocol == ExchangeProtocol.GRPC
            ? ServiceExchangeClientType.GRPC : ServiceExchangeClientType.RESTCLIENT;
    }

    /** @deprecated Use {@link #getBaseUrl()}. */
    @Deprecated(forRemoval = true)
    public HttpClientProperties getHttp() {
        HttpClientProperties compatibility = new HttpClientProperties();
        compatibility.setBaseUrl(baseUrl);
        return compatibility;
    }

    /** @deprecated Use {@link #setBaseUrl(String)}. */
    @Deprecated(forRemoval = true)
    public void setHttp(HttpClientProperties compatibility) {
        baseUrl = compatibility == null ? null : compatibility.getBaseUrl();
    }

    /** @deprecated Use {@link #getGrpcChannel()}. */
    @Deprecated(forRemoval = true)
    public GrpcClientProperties getGrpc() {
        GrpcClientProperties compatibility = new GrpcClientProperties();
        compatibility.setAddress(grpcChannel);
        return compatibility;
    }

    /** @deprecated Use {@link #setGrpcChannel(String)}. */
    @Deprecated(forRemoval = true)
    public void setGrpc(GrpcClientProperties compatibility) {
        grpcChannel = compatibility == null ? null : compatibility.getAddress();
    }

    /** @deprecated Use {@link #getSslBundle()}. */
    @Deprecated(forRemoval = true)
    public SslProperties getSsl() {
        SslProperties compatibility = new SslProperties();
        compatibility.setBundle(sslBundle);
        compatibility.setEnabled(sslBundle != null && !sslBundle.isBlank());
        return compatibility;
    }

    /** @deprecated Use {@link #setSslBundle(String)}. */
    @Deprecated(forRemoval = true)
    public void setSsl(SslProperties compatibility) {
        sslBundle = compatibility == null ? null : compatibility.getBundle();
    }

    /** @deprecated Proxy tuning is native-client configuration. */
    @Deprecated(forRemoval = true)
    public ProxyProperties getProxy() { return new ProxyProperties(); }

    /** @deprecated Proxy tuning is native-client configuration. */
    @Deprecated(forRemoval = true)
    public void setProxy(ProxyProperties ignored) { }

    /** @deprecated Use {@link #isResilienceEnabled()}. */
    @Deprecated(forRemoval = true)
    public ResilienceProperties getResilience() {
        ResilienceProperties compatibility = new ResilienceProperties();
        compatibility.setEnabled(resilienceEnabled);
        return compatibility;
    }

    /** @deprecated Use {@link #setResilienceEnabled(boolean)}. */
    @Deprecated(forRemoval = true)
    public void setResilience(ResilienceProperties compatibility) {
        resilienceEnabled = compatibility != null && compatibility.isEnabled();
    }
}
