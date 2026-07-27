package com.company.platform.core.audit.jpa;

import org.springframework.data.domain.AuditorAware;

import java.util.Objects;
import java.util.Optional;

/** Resolves a stable service auditor when no security integration is available. */
public final class SystemAuditorAware implements AuditorAware<String> {

    private final String auditor;

    public SystemAuditorAware(String auditor) {
        this.auditor = Objects.requireNonNull(auditor, "auditor must not be null");
    }

    @Override
    public Optional<String> getCurrentAuditor() {
        return Optional.of(auditor);
    }
}
