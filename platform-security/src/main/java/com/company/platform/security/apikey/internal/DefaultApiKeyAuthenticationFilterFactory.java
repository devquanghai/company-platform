package com.company.platform.security.apikey.internal;

import com.company.platform.security.apikey.api.ApiKeyAuthenticationFilterFactory;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.authentication.AuthenticationFilter;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.util.matcher.RequestMatcher;

public final class DefaultApiKeyAuthenticationFilterFactory implements ApiKeyAuthenticationFilterFactory {
    private final ApiKeyAuthenticationProvider provider;
    private final ApiKeyAuthenticationConverter converter;
    private final AuthenticationEntryPoint entryPoint;

    public DefaultApiKeyAuthenticationFilterFactory(ApiKeyAuthenticationProvider provider,
                                                    ApiKeyAuthenticationConverter converter,
                                                    AuthenticationEntryPoint entryPoint) {
        this.provider = provider;
        this.converter = converter;
        this.entryPoint = entryPoint;
    }

    @Override
    public Filter create(RequestMatcher requestMatcher) {
        var filter = new AuthenticationFilter(new ProviderManager(provider), converter);
        filter.setRequestMatcher(requestMatcher);
        filter.setSecurityContextRepository(new RequestAttributeSecurityContextRepository());
        filter.setSuccessHandler(new ContinueFilterChainSuccessHandler());
        filter.setFailureHandler((request, response, exception) -> entryPoint.commence(request, response, exception));
        return filter;
    }

    private static final class ContinueFilterChainSuccessHandler implements AuthenticationSuccessHandler {
        @Override
        public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                            Authentication authentication) {
            // Spring Security 7 calls the chain-aware overload below.
        }

        @Override
        public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                            FilterChain chain, Authentication authentication)
            throws IOException, ServletException {
            chain.doFilter(request, response);
        }
    }
}
