package com.company.platform.security.authorization.api;

import org.springframework.security.core.Authentication;

@FunctionalInterface
public interface TenantAuthorization {
    boolean canAccess(Authentication authentication, String requestedTenantId);
}
