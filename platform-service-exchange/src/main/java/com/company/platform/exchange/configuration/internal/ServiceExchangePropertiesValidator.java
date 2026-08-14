package com.company.platform.exchange.configuration.internal;

import com.company.platform.exchange.api.client.ServiceExchangeClientType;
import com.company.platform.exchange.autoconfigure.properties.ClientProperties;
import com.company.platform.exchange.autoconfigure.properties.ServiceExchangeProperties;
import com.company.platform.exchange.domain.exception.InvalidClientConfigurationException;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.ssl.NoSuchSslBundleException;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

public final class ServiceExchangePropertiesValidator {
    private static final Pattern CLIENT_NAME = Pattern.compile("[a-z0-9][a-z0-9._-]*");
    private static final Set<String> ROOT_KEYS = Set.of("enabled", "clients");
    private static final Set<String> CLIENT_KEYS = Set.of(
        "enabled", "type", "base-url", "grpc-channel", "resilience-instance",
        "ssl-bundle", "resilience-enabled", "observability-enabled",
        "logging", "audit");

    private final ServiceExchangeProperties properties;
    private final SslBundles sslBundles;
    private final Environment environment;

    public ServiceExchangePropertiesValidator(
        ServiceExchangeProperties properties,
        Optional<SslBundles> sslBundles,
        Environment environment
    ) {
        this.properties = properties;
        this.sslBundles = sslBundles.orElse(null);
        this.environment = environment;
    }

    public void validate() {
        rejectLegacyProperties();
        validateRedirectPolicy();
        properties.getClients().forEach(this::validate);
    }

    private void validateRedirectPolicy() {
        boolean hasHttpClient = properties.getClients().values().stream()
            .anyMatch(client -> client.isEnabled()
                && client.getType() != ServiceExchangeClientType.GRPC);
        String redirects = environment.getProperty("spring.http.clients.redirects", "");
        if (hasHttpClient && !"dont-follow".equalsIgnoreCase(redirects)
            && !"DONT_FOLLOW".equalsIgnoreCase(redirects)) {
            fail("<root>",
                "spring.http.clients.redirects must be DONT_FOLLOW to preserve named-client origin");
        }
    }

    private void validate(String name, ClientProperties client) {
        if (!StringUtils.hasText(name) || !CLIENT_NAME.matcher(name).matches()) {
            fail(name, "client name must match " + CLIENT_NAME);
        }
        if (!client.isEnabled()) {
            return;
        }
        if (client.getType() == null) {
            fail(name, "type is required");
        }
        if (!client.getLogging().getSensitiveHeaders().isEmpty()
            || !client.getLogging().getSensitiveFields().isEmpty()
            || !client.getLogging().getSensitiveQueryParameters().isEmpty()) {
            fail(name,
                "per-client sensitive rules are unsupported; configure platform-logging masking");
        }
        if (client.getType() == ServiceExchangeClientType.GRPC) {
            if (!StringUtils.hasText(client.getGrpcChannel())) {
                fail(name, "grpc-channel is required for GRPC");
            }
            if (StringUtils.hasText(client.getBaseUrl())
                || StringUtils.hasText(client.getSslBundle())) {
                fail(name, "GRPC transport is configured under native spring.grpc.client.*");
            }
            return;
        }
        validateBaseUrl(name, client.getBaseUrl());
        validateSslBundle(name, client.getSslBundle());
    }

    private void validateBaseUrl(String name, String value) {
        if (!StringUtils.hasText(value)) {
            fail(name, "base-url is required");
        }
        try {
            URI uri = URI.create(value);
            if (!("http".equalsIgnoreCase(uri.getScheme())
                || "https".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null || uri.getRawUserInfo() != null
                || uri.getRawFragment() != null || uri.getRawQuery() != null
                || (StringUtils.hasText(uri.getRawPath()) && !"/".equals(uri.getRawPath()))) {
                fail(name, "base-url must be an HTTP(S) origin without user-info, query or fragment");
            }
        } catch (IllegalArgumentException exception) {
            fail(name, "base-url is invalid");
        }
    }

    private void validateSslBundle(String name, String bundle) {
        if (!StringUtils.hasText(bundle)) {
            return;
        }
        if (sslBundles == null) {
            fail(name, "SSL Bundle support is unavailable");
        }
        try {
            sslBundles.getBundle(bundle);
        } catch (NoSuchSslBundleException exception) {
            fail(name, "SSL Bundle '" + bundle + "' does not exist");
        }
    }

    @SuppressWarnings("unchecked")
    private void rejectLegacyProperties() {
        Map<String, Object> root = Binder.get(environment)
            .bind("platform.service-exchange", Bindable.mapOf(String.class, Object.class))
            .orElse(Map.of());
        root.keySet().stream().filter(key -> !ROOT_KEYS.contains(key)).findFirst()
            .ifPresent(key -> fail("<root>",
                "unsupported legacy property platform.service-exchange." + key));
        Object clients = root.get("clients");
        if (!(clients instanceof Map<?, ?> clientMap)) {
            return;
        }
        clientMap.forEach((name, raw) -> {
            if (!(raw instanceof Map<?, ?> values)) {
                return;
            }
            values.keySet().stream().map(String::valueOf)
                .filter(key -> !CLIENT_KEYS.contains(key)).findFirst()
                .ifPresent(key -> fail(String.valueOf(name),
                    "unsupported legacy client property '" + key + "'"));
        });
    }

    private static void fail(String client, String detail) {
        throw new InvalidClientConfigurationException(
            StringUtils.hasText(client) ? client : "<empty>", detail);
    }
}
