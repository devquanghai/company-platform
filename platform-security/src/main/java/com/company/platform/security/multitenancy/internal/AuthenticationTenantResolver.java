package com.company.platform.security.multitenancy.internal;

import com.company.platform.security.context.internal.SecurityIdentityExtractor;
import com.company.platform.security.multitenancy.api.TenantIdentity;
import com.company.platform.security.multitenancy.api.TenantResolver;
import java.util.List;
import java.util.Optional;
import org.springframework.security.core.Authentication;

public final class AuthenticationTenantResolver implements TenantResolver {
    private final List<SecurityIdentityExtractor> extractors;

    public AuthenticationTenantResolver(List<SecurityIdentityExtractor> extractors) {
        this.extractors = List.copyOf(extractors);
    }

    @Override
    public Optional<TenantIdentity> resolve(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        for (SecurityIdentityExtractor extractor : extractors) {
            var identity = extractor.extract(authentication);
            if (identity.isPresent() && identity.get().tenantId() != null && identity.get().tenantSource() != null) {
                return Optional.of(new TenantIdentity(identity.get().tenantId(), identity.get().tenantSource()));
            }
        }
        return Optional.empty();
    }
}
