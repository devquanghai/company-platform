package com.company.platform.security.context.internal;

import com.company.platform.security.apikey.api.ApiKeyPrincipal;
import com.company.platform.security.multitenancy.api.TenantIdentity;
import java.util.LinkedHashMap;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;

public final class CoreSecurityIdentityExtractor implements SecurityIdentityExtractor {
    @Override
    public Optional<ExtractedSecurityIdentity> extract(Authentication authentication) {
        if (authentication.getPrincipal() instanceof ApiKeyPrincipal principal) {
            var attributes = new LinkedHashMap<String, Object>(principal.attributes());
            attributes.put("tenant_id", principal.tenantId());
            return Optional.of(new ExtractedSecurityIdentity(principal.id(), principal.name(), principal.tenantId(),
                TenantIdentity.Source.API_KEY, attributes));
        }
        if (authentication.getPrincipal() instanceof UserDetails user) {
            return Optional.of(new ExtractedSecurityIdentity(null, user.getUsername(), null, null, null));
        }
        return Optional.empty();
    }
}
