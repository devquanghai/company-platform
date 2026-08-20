package com.company.platform.security.context.internal;

import com.company.platform.security.multitenancy.api.TenantIdentity;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;

public final class OAuth2SecurityIdentityExtractor implements SecurityIdentityExtractor {
    @Override
    public Optional<ExtractedSecurityIdentity> extract(Authentication authentication) {
        if (!(authentication instanceof OAuth2AuthenticationToken oauth2)) {
            return Optional.empty();
        }
        var attributes = oauth2.getPrincipal().getAttributes();
        String tenantId = string(attributes.get("tenant_id"));
        if (tenantId == null) {
            tenantId = string(attributes.get("iss"));
        }
        String subject = string(attributes.get("sub"));
        String username = string(attributes.get("preferred_username"));
        return Optional.of(new ExtractedSecurityIdentity(subject,
            username == null ? authentication.getName() : username, tenantId,
            TenantIdentity.Source.OIDC, attributes));
    }

    private static String string(Object value) {
        return value == null || value.toString().isBlank() ? null : value.toString();
    }
}
