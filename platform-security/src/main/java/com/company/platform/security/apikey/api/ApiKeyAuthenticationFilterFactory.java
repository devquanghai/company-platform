package com.company.platform.security.apikey.api;

import jakarta.servlet.Filter;
import org.springframework.security.web.util.matcher.RequestMatcher;

/** Creates a Spring Security-chain-only API-key filter for an application-owned request matcher. */
@FunctionalInterface
public interface ApiKeyAuthenticationFilterFactory {
    Filter create(RequestMatcher requestMatcher);
}
