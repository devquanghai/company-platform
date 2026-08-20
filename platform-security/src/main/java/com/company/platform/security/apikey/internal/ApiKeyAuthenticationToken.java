package com.company.platform.security.apikey.internal;

import com.company.platform.security.apikey.api.ApiKeyPrincipal;
import java.util.Collection;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

public final class ApiKeyAuthenticationToken extends AbstractAuthenticationToken {
    private final Object principal;
    private Object credentials;

    private ApiKeyAuthenticationToken(Object principal, Object credentials,
                                      Collection<? extends GrantedAuthority> authorities,
                                      boolean authenticated) {
        super(authorities);
        this.principal = principal;
        this.credentials = credentials;
        super.setAuthenticated(authenticated);
    }

    static ApiKeyAuthenticationToken unauthenticated(String rawApiKey) {
        return new ApiKeyAuthenticationToken("api-key", rawApiKey, null, false);
    }

    static ApiKeyAuthenticationToken authenticated(ApiKeyPrincipal principal,
                                                    Collection<? extends GrantedAuthority> authorities) {
        return new ApiKeyAuthenticationToken(principal, null, authorities, true);
    }

    @Override
    public Object getCredentials() {
        return credentials;
    }

    @Override
    public Object getPrincipal() {
        return principal;
    }

    @Override
    public void eraseCredentials() {
        super.eraseCredentials();
        credentials = null;
    }

    @Override
    public void setAuthenticated(boolean authenticated) {
        if (authenticated) {
            throw new IllegalArgumentException("Use the authenticated factory method");
        }
        super.setAuthenticated(false);
    }
}
