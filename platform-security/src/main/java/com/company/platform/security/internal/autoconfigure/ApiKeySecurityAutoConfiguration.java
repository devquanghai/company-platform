package com.company.platform.security.internal.autoconfigure;

import com.company.platform.security.apikey.api.ApiKeyAuthenticationFilterFactory;
import com.company.platform.security.apikey.api.ApiKeyResolver;
import com.company.platform.security.apikey.api.ApiKeyValidator;
import com.company.platform.security.apikey.internal.ApiKeyAuthenticationConverter;
import com.company.platform.security.apikey.internal.ApiKeyAuthenticationProvider;
import com.company.platform.security.apikey.internal.DefaultApiKeyAuthenticationFilterFactory;
import com.company.platform.security.apikey.internal.DefaultApiKeyResolver;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.authentication.AuthenticationFilter;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

@AutoConfiguration(after = PlatformSecurityAutoConfiguration.class)
@ConditionalOnClass(AuthenticationFilter.class)
@ConditionalOnBean(ApiKeyValidator.class)
public class ApiKeySecurityAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    ApiKeyResolver apiKeyResolver() {
        return new DefaultApiKeyResolver();
    }

    @Bean
    @ConditionalOnMissingBean
    ApiKeyAuthenticationConverter apiKeyAuthenticationConverter(ApiKeyResolver resolver) {
        return new ApiKeyAuthenticationConverter(resolver);
    }

    @Bean
    @ConditionalOnMissingBean
    ApiKeyAuthenticationProvider apiKeyAuthenticationProvider(ApiKeyValidator validator) {
        return new ApiKeyAuthenticationProvider(validator);
    }

    @Bean
    @ConditionalOnMissingBean
    ApiKeyAuthenticationFilterFactory apiKeyAuthenticationFilterFactory(
        ApiKeyAuthenticationProvider provider,
        ApiKeyAuthenticationConverter converter,
        ObjectProvider<AuthenticationEntryPoint> entryPoints
    ) {
        AuthenticationEntryPoint entryPoint = entryPoints.orderedStream().findFirst()
            .orElseGet(() -> new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED));
        return new DefaultApiKeyAuthenticationFilterFactory(provider, converter, entryPoint);
    }
}
