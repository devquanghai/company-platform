package com.company.platform.exchange.support;

import com.company.platform.exchange.autoconfigure.properties.ClientProperties;
import com.company.platform.exchange.autoconfigure.properties.ServiceExchangeProperties;
import com.company.platform.exchange.domain.exception.InvalidClientConfigurationException;
import com.company.platform.exchange.domain.model.ExchangeProtocol;
import org.springframework.boot.ssl.NoSuchSslBundleException;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

public final class ServiceExchangePropertiesValidator {

    private static final Pattern CLIENT_NAME = Pattern.compile("[a-z0-9][a-z0-9._-]*");
    private final ServiceExchangeProperties properties;
    private final SslBundles sslBundles;

    public ServiceExchangePropertiesValidator(
        ServiceExchangeProperties properties, Optional<SslBundles> sslBundles
    ) {
        this.properties = properties;
        this.sslBundles = sslBundles.orElse(null);
    }

    public void validate() {
        for (Map.Entry<String, ClientProperties> entry : properties.getClients().entrySet()) {
            validate(entry.getKey(), entry.getValue());
        }
    }

    private void validate(String name, ClientProperties client) {
        if (!StringUtils.hasText(name) || !CLIENT_NAME.matcher(name).matches()) {
            fail(name, "client name must match " + CLIENT_NAME);
        }
        if (client.getProtocol() == null) {
            fail(name, "protocol is required");
        }
        if (!client.isEnabled()) {
            return;
        }
        if (client.getProtocol() == ExchangeProtocol.HTTP
            && !StringUtils.hasText(client.getHttp().getBaseUrl())) {
            fail(name, "http.base-url is required");
        }
        if (client.getProtocol() == ExchangeProtocol.GRPC
            && !StringUtils.hasText(client.getGrpc().getAddress())) {
            fail(name, "grpc.address is required");
        }
        positive(name, "http.connect-timeout", client.getHttp().getConnectTimeout());
        positive(name, "http.response-timeout", client.getHttp().getResponseTimeout());
        positive(name, "grpc.default-deadline", client.getGrpc().getDefaultDeadline());
        if (client.getProxy().isEnabled()
            && (!StringUtils.hasText(client.getProxy().getHost())
                || client.getProxy().getPort() < 1 || client.getProxy().getPort() > 65535)) {
            fail(name, "proxy host and port 1..65535 are required");
        }
        if (client.getResilience().getRetry().getMaxAttempts() < 1) {
            fail(name, "resilience.retry.max-attempts must be at least 1");
        }
        if (client.getResilience().getRateLimiter().getLimitForPeriod() < 1
            || client.getResilience().getRateLimiter().getLimitRefreshPeriod().isZero()
            || client.getResilience().getRateLimiter().getLimitRefreshPeriod().isNegative()) {
            fail(name, "resilience.rate-limiter values must be positive");
        }
        validateSsl(name, client);
    }

    private void validateSsl(String name, ClientProperties client) {
        if (client.getSsl().isTrustAll()
            || !client.getSsl().isHostnameVerificationEnabled()) {
            boolean production = properties.getEnvironment().equalsIgnoreCase("production")
                || properties.getEnvironment().equalsIgnoreCase("prod");
            if (!properties.isAllowInsecureSsl() || production) {
                fail(name, "insecure SSL options require allow-insecure-ssl outside production");
            }
        }
        if (!client.getSsl().isEnabled()) {
            if (client.getSsl().isTrustAll()
                || !client.getSsl().isHostnameVerificationEnabled()
                || StringUtils.hasText(client.getSsl().getBundle())) {
                fail(name, "ssl.enabled must be true when SSL options are configured");
            }
            return;
        }
        if (client.getProtocol() == ExchangeProtocol.GRPC
            && client.getGrpc().getNegotiationType()
                == com.company.platform.exchange.autoconfigure.properties.GrpcNegotiationType.MTLS
            && !StringUtils.hasText(client.getSsl().getBundle())) {
            fail(name, "gRPC mTLS requires ssl.bundle with key material");
        }
        if (StringUtils.hasText(client.getSsl().getBundle())) {
            if (sslBundles == null) {
                fail(name, "SSL Bundle support is unavailable");
            }
            try {
                sslBundles.getBundle(client.getSsl().getBundle());
            } catch (NoSuchSslBundleException exception) {
                fail(name, "SSL Bundle does not exist");
            }
        }
    }

    private static void positive(String client, String field, Duration duration) {
        if (duration == null || duration.isNegative() || duration.isZero()) {
            fail(client, field + " must be positive");
        }
    }

    private static void fail(String client, String detail) {
        throw new InvalidClientConfigurationException(
            StringUtils.hasText(client) ? client : "<empty>", detail);
    }
}
