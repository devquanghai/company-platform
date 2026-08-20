package com.company.platform.security.authorization.internal;

import com.company.platform.security.authorization.api.TenantAuthorization;
import com.company.platform.security.multitenancy.api.TenantResolver;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import org.springframework.security.core.Authentication;

public final class DefaultTenantAuthorization implements TenantAuthorization {
    private final TenantResolver tenantResolver;

    public DefaultTenantAuthorization(TenantResolver tenantResolver) {
        this.tenantResolver = tenantResolver;
    }

    @Override
    public boolean canAccess(Authentication authentication, String requestedTenantId) {
        if (requestedTenantId == null || requestedTenantId.isBlank()) {
            return false;
        }
        return tenantResolver.resolve(authentication)
            .map(identity -> constantTimeEquals(identity.tenantId(), requestedTenantId))
            .orElse(false);
    }

    private static boolean constantTimeEquals(String actual, String requested) {
        return MessageDigest.isEqual(actual.getBytes(StandardCharsets.UTF_8),
            requested.getBytes(StandardCharsets.UTF_8));
    }
}
