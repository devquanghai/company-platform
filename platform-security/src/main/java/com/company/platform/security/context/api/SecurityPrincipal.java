package com.company.platform.security.context.api;

import java.util.Map;
import java.util.Set;

/** A sanitized, immutable view of the current authenticated identity. */
public record SecurityPrincipal(
    String subject,
    String username,
    String tenantId,
    Set<String> authorities,
    String authenticationType,
    Map<String, Object> attributes
) {
    public SecurityPrincipal {
        authorities = authorities == null ? Set.of() : Set.copyOf(authorities);
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }
}
