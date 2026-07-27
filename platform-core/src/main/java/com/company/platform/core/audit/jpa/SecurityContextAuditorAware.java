package com.company.platform.core.audit.jpa;

import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Objects;
import java.util.Optional;

/** Resolves the authenticated principal and falls back to a configured service auditor. */
public final class SecurityContextAuditorAware implements AuditorAware<String> {

    private final String fallbackAuditor;

    public SecurityContextAuditorAware(String fallbackAuditor) {
        this.fallbackAuditor = Objects.requireNonNull(
            fallbackAuditor,
            "fallbackAuditor must not be null"
        );
    }

    @Override
    public Optional<String> getCurrentAuditor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return Optional.ofNullable(authentication)
            .filter(Authentication::isAuthenticated)
            .map(Authentication::getName)
            .filter(name -> !name.isBlank())
            .or(() -> Optional.of(fallbackAuditor));
    }
}
