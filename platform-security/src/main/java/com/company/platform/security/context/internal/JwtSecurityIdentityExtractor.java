package com.company.platform.security.context.internal;

import com.company.platform.security.multitenancy.api.TenantIdentity;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

public final class JwtSecurityIdentityExtractor implements SecurityIdentityExtractor {
    @Override
    public Optional<ExtractedSecurityIdentity> extract(Authentication authentication) {
        if (!(authentication instanceof JwtAuthenticationToken jwt)) {
            return Optional.empty();
        }
        var attributes = jwt.getTokenAttributes();
        String tenantId = string(attributes.get("tenant_id"));
        TenantIdentity.Source source = TenantIdentity.Source.JWT_CLAIM;
        if (tenantId == null && jwt.getToken().getIssuer() != null) {
            tenantId = jwt.getToken().getIssuer().toString();
            source = TenantIdentity.Source.JWT_ISSUER;
        }
        String username = string(attributes.get("preferred_username"));
        return Optional.of(new ExtractedSecurityIdentity(jwt.getToken().getSubject(),
            username == null ? authentication.getName() : username, tenantId, source, attributes));
    }

    private static String string(Object value) {
        return value == null || value.toString().isBlank() ? null : value.toString();
    }
}
