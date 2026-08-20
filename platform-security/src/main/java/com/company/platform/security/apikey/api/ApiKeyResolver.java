package com.company.platform.security.apikey.api;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;

@FunctionalInterface
public interface ApiKeyResolver {
    Optional<String> resolve(HttpServletRequest request);
}
