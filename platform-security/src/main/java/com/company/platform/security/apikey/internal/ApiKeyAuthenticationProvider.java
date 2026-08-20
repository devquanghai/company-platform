package com.company.platform.security.apikey.internal;

import com.company.platform.security.apikey.api.ApiKeyPrincipal;
import com.company.platform.security.apikey.api.ApiKeyValidator;
import java.time.Instant;
import java.util.LinkedHashSet;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

public final class ApiKeyAuthenticationProvider implements AuthenticationProvider {
    private final ApiKeyValidator validator;

    public ApiKeyAuthenticationProvider(ApiKeyValidator validator) {
        this.validator = validator;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String rawApiKey = (String) authentication.getCredentials();
        ApiKeyPrincipal principal = validator.validate(rawApiKey);
        if (principal == null || principal.expiresAt() != null && !principal.expiresAt().isAfter(Instant.now())) {
            throw new BadCredentialsException("Invalid API key");
        }

        var authorities = new LinkedHashSet<SimpleGrantedAuthority>();
        principal.authorities().stream().filter(ApiKeyAuthenticationProvider::hasText)
            .map(SimpleGrantedAuthority::new).forEach(authorities::add);
        principal.scopes().stream().filter(ApiKeyAuthenticationProvider::hasText)
            .map(scope -> new SimpleGrantedAuthority("SCOPE_" + scope)).forEach(authorities::add);
        ((ApiKeyAuthenticationToken) authentication).eraseCredentials();
        return ApiKeyAuthenticationToken.authenticated(principal, authorities);
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return ApiKeyAuthenticationToken.class.isAssignableFrom(authentication);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
