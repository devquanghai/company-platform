package com.company.platform.security.context.internal;

import com.company.platform.security.context.api.CurrentSecurityContext;
import com.company.platform.security.context.api.SecurityPrincipal;
import com.company.platform.security.multitenancy.api.TenantResolver;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolderStrategy;

public final class DefaultCurrentSecurityContext implements CurrentSecurityContext {
    private static final Set<String> EXPOSED_ATTRIBUTES = Set.of("iss", "tenant_id", "organization");

    private final SecurityContextHolderStrategy contextHolderStrategy;
    private final TenantResolver tenantResolver;
    private final List<SecurityIdentityExtractor> extractors;

    public DefaultCurrentSecurityContext(SecurityContextHolderStrategy contextHolderStrategy,
                                         TenantResolver tenantResolver,
                                         List<SecurityIdentityExtractor> extractors) {
        this.contextHolderStrategy = contextHolderStrategy;
        this.tenantResolver = tenantResolver;
        this.extractors = List.copyOf(extractors);
    }

    @Override
    public Optional<SecurityPrincipal> principal() {
        Authentication authentication = contextHolderStrategy.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
            || authentication instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }
        ExtractedSecurityIdentity identity = extractors.stream().map(extractor -> extractor.extract(authentication))
            .flatMap(Optional::stream).findFirst()
            .orElseGet(() -> new ExtractedSecurityIdentity(null, authentication.getName(), null, null, null));
        String tenantId = tenantResolver.resolve(authentication).map(tenant -> tenant.tenantId()).orElse(null);
        Set<String> authorities = authentication.getAuthorities().stream()
            .map(authority -> authority.getAuthority()).collect(Collectors.toUnmodifiableSet());
        return Optional.of(new SecurityPrincipal(identity.subject(), identity.username(), tenantId, authorities,
            authentication.getClass().getSimpleName(), sanitized(identity.attributes())));
    }

    private static Map<String, Object> sanitized(Map<String, Object> source) {
        var result = new LinkedHashMap<String, Object>();
        EXPOSED_ATTRIBUTES.forEach(key -> {
            Object value = source.get(key);
            if (value != null) {
                result.put(key, value);
            }
        });
        return result;
    }
}
