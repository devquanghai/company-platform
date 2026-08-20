package com.company.platform.security.context.api;

import java.util.Optional;
import java.util.Set;

public interface CurrentSecurityContext {
    Optional<SecurityPrincipal> principal();

    default Optional<String> subject() {
        return principal().map(SecurityPrincipal::subject);
    }

    default Optional<String> username() {
        return principal().map(SecurityPrincipal::username);
    }

    default Optional<String> tenantId() {
        return principal().map(SecurityPrincipal::tenantId);
    }

    default Set<String> authorities() {
        return principal().map(SecurityPrincipal::authorities).orElseGet(Set::of);
    }

    default boolean authenticated() {
        return principal().isPresent();
    }
}
