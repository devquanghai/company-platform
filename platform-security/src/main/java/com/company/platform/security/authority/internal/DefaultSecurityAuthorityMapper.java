package com.company.platform.security.authority.internal;

import com.company.platform.security.authority.api.SecurityAuthorityMapper;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

public final class DefaultSecurityAuthorityMapper implements SecurityAuthorityMapper {
    @Override
    public Collection<? extends GrantedAuthority> map(Map<String, Object> attributes) {
        var values = new LinkedHashSet<String>();
        addScopes(values, attributes.get("scope"));
        addScopes(values, attributes.get("scp"));
        addAuthorities(values, attributes.get("authorities"), "");
        addAuthorities(values, attributes.get("roles"), "ROLE_");
        addAuthorities(values, attributes.get("groups"), "ROLE_");
        addKeycloak(values, attributes);
        return values.stream().map(SimpleGrantedAuthority::new).toList();
    }

    private static void addScopes(Collection<String> target, Object claim) {
        if (claim instanceof String scopes) {
            for (String scope : scopes.split("\\s+")) {
                add(target, "SCOPE_", scope);
            }
        } else if (claim instanceof Collection<?> scopes) {
            scopes.forEach(scope -> add(target, "SCOPE_", scope));
        }
    }

    private static void addAuthorities(Collection<String> target, Object claim, String prefix) {
        if (claim instanceof String value) {
            for (String authority : value.split("[\\s,]+")) {
                add(target, prefix, authority);
            }
        } else if (claim instanceof Collection<?> values) {
            values.forEach(value -> add(target, prefix, value));
        }
    }

    private static void addKeycloak(Collection<String> target, Map<String, Object> attributes) {
        if (attributes.get("realm_access") instanceof Map<?, ?> realm) {
            addAuthorities(target, realm.get("roles"), "ROLE_");
        }
        if (attributes.get("resource_access") instanceof Map<?, ?> resources) {
            resources.values().stream().filter(Map.class::isInstance).map(Map.class::cast)
                .forEach(resource -> addAuthorities(target, resource.get("roles"), "ROLE_"));
        }
    }

    private static void add(Collection<String> target, String prefix, Object raw) {
        if (raw == null) {
            return;
        }
        String value = raw.toString().trim();
        if (!value.isEmpty()) {
            target.add(prefix.isEmpty() || value.startsWith(prefix) ? value : prefix + value);
        }
    }
}
