package com.company.platform.security.apikey.api;

import java.security.Principal;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

/** Validated API-key identity. Raw key material must never be placed in this type. */
public record ApiKeyPrincipal(
    String id,
    String name,
    String tenantId,
    Set<String> authorities,
    Set<String> scopes,
    Instant expiresAt,
    Map<String, Object> attributes
) implements Principal {
    public ApiKeyPrincipal {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("API key principal id is required");
        }
        name = name == null || name.isBlank() ? id : name;
        authorities = authorities == null ? Set.of() : Set.copyOf(authorities);
        scopes = scopes == null ? Set.of() : Set.copyOf(scopes);
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    @Override
    public String getName() {
        return name;
    }
}
