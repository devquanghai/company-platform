package com.company.platform.security.apikey.internal;

import com.company.platform.security.apikey.api.ApiKeyResolver;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;

public final class DefaultApiKeyResolver implements ApiKeyResolver {
    private static final String HEADER = "X-API-Key";

    @Override
    public Optional<String> resolve(HttpServletRequest request) {
        String value = request.getHeader(HEADER);
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value);
    }
}
