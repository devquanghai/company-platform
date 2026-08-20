package com.company.platform.security.context.internal;

import com.company.platform.security.multitenancy.api.TenantIdentity;
import java.util.Map;

public record ExtractedSecurityIdentity(
    String subject,
    String username,
    String tenantId,
    TenantIdentity.Source tenantSource,
    Map<String, Object> attributes
) {
    public ExtractedSecurityIdentity {
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }
}
