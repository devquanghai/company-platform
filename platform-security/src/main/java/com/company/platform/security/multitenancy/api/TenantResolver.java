package com.company.platform.security.multitenancy.api;

import java.util.Optional;
import org.springframework.security.core.Authentication;

@FunctionalInterface
public interface TenantResolver {
    Optional<TenantIdentity> resolve(Authentication authentication);
}
