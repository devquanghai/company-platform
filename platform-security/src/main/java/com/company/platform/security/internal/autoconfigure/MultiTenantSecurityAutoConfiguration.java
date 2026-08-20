package com.company.platform.security.internal.autoconfigure;

import com.company.platform.security.multitenancy.api.TrustedIssuerRepository;
import com.company.platform.security.multitenancy.internal.TrustedIssuerAuthenticationManagerResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.security.authentication.AuthenticationManagerResolver;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtIssuerAuthenticationManagerResolver;

@AutoConfiguration(after = JwtAuthorityAutoConfiguration.class)
@ConditionalOnClass(JwtIssuerAuthenticationManagerResolver.class)
@ConditionalOnBean({TrustedIssuerRepository.class, JwtAuthenticationConverter.class})
public class MultiTenantSecurityAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(AuthenticationManagerResolver.class)
    AuthenticationManagerResolver<HttpServletRequest> trustedIssuerAuthenticationManagerResolver(
        TrustedIssuerRepository trustedIssuers,
        JwtAuthenticationConverter converter
    ) {
        return new TrustedIssuerAuthenticationManagerResolver(trustedIssuers, converter).create();
    }
}
