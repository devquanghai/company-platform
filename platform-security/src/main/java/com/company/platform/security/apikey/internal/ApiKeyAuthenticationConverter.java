package com.company.platform.security.apikey.internal;

import com.company.platform.security.apikey.api.ApiKeyResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationConverter;

public final class ApiKeyAuthenticationConverter implements AuthenticationConverter {
    private final ApiKeyResolver resolver;

    public ApiKeyAuthenticationConverter(ApiKeyResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    public Authentication convert(HttpServletRequest request) {
        return resolver.resolve(request).map(ApiKeyAuthenticationToken::unauthenticated).orElse(null);
    }
}
