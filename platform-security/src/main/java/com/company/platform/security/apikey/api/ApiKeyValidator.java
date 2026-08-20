package com.company.platform.security.apikey.api;

/** Application-owned validation port; implementations may use a database, vault or external IAM. */
@FunctionalInterface
public interface ApiKeyValidator {
    ApiKeyPrincipal validate(String rawApiKey);
}
