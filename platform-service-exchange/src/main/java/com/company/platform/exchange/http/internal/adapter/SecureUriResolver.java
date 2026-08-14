package com.company.platform.exchange.http.internal.adapter;

import com.company.platform.exchange.api.http.ExchangeRequest;
import com.company.platform.exchange.domain.exception.InvalidClientConfigurationException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

public final class SecureUriResolver {

    public URI resolve(ExchangeRequest request, String baseUrl) {
        var supplied = UriComponentsBuilder.fromUriString(request.getPath()).build();
        boolean absolute = supplied.getScheme() != null;
        boolean networkPath = supplied.getScheme() == null
            && (request.getPath().startsWith("//") || supplied.getHost() != null);
        if (absolute || networkPath) {
            throw new InvalidClientConfigurationException(
                request.getClientName(), "absolute or network-path URI is not allowed");
        }
        if (supplied.getUserInfo() != null || supplied.getFragment() != null) {
            throw new InvalidClientConfigurationException(
                request.getClientName(), "URI user-info and fragment are not allowed");
        }
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(baseUrl)
            .path(request.getPath());
        request.getQueryParameters().forEach(builder::queryParam);
        URI result = builder.buildAndExpand(request.getPathVariables()).encode().toUri().normalize();
        if (result.getHost() == null || result.getRawUserInfo() != null
            || result.getRawFragment() != null) {
            throw new InvalidClientConfigurationException(
                request.getClientName(), "resolved URI is not a safe HTTP target");
        }
        return result;
    }
}
