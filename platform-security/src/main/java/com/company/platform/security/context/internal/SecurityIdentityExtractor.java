package com.company.platform.security.context.internal;

import java.util.Optional;
import org.springframework.security.core.Authentication;

public interface SecurityIdentityExtractor {
    Optional<ExtractedSecurityIdentity> extract(Authentication authentication);
}
