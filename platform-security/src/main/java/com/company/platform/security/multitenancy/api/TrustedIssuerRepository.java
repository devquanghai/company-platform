package com.company.platform.security.multitenancy.api;

import java.net.URI;

/** Trust decision must precede issuer discovery or remote JWK access. */
@FunctionalInterface
public interface TrustedIssuerRepository {
    boolean isTrusted(URI issuer);
}
