package com.company.platform.security.multitenancy.api;

public record TenantIdentity(String tenantId, Source source) {
    public TenantIdentity {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId is required");
        }
    }

    public enum Source {
        JWT_CLAIM,
        JWT_ISSUER,
        OIDC,
        API_KEY
    }
}
