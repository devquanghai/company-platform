package com.company.platform.security.internal.autoconfigure;

import com.company.platform.security.authorization.api.TenantAuthorization;
import com.company.platform.security.authorization.internal.DefaultTenantAuthorization;
import com.company.platform.security.context.api.CurrentSecurityContext;
import com.company.platform.security.context.internal.DefaultCurrentSecurityContext;
import com.company.platform.security.context.internal.CoreSecurityIdentityExtractor;
import com.company.platform.security.context.internal.SecurityIdentityExtractor;
import com.company.platform.security.multitenancy.api.TenantResolver;
import com.company.platform.security.multitenancy.internal.AuthenticationTenantResolver;
import com.company.platform.security.web.api.SecurityProblemDetailFactory;
import com.company.platform.security.web.internal.DefaultSecurityProblemDetailFactory;
import com.company.platform.security.web.internal.ProblemDetailAccessDeniedHandler;
import com.company.platform.security.web.internal.ProblemDetailAuthenticationEntryPoint;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import tools.jackson.databind.ObjectMapper;
import java.util.List;

@AutoConfiguration(afterName = "org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration")
@ConditionalOnClass(SecurityContextHolder.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class PlatformSecurityAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    CoreSecurityIdentityExtractor coreSecurityIdentityExtractor() {
        return new CoreSecurityIdentityExtractor();
    }

    @Bean
    @ConditionalOnMissingBean
    TenantResolver tenantResolver(List<SecurityIdentityExtractor> extractors) {
        return new AuthenticationTenantResolver(extractors);
    }

    @Bean
    @ConditionalOnMissingBean
    CurrentSecurityContext currentSecurityContext(TenantResolver tenantResolver,
                                                  List<SecurityIdentityExtractor> extractors) {
        return new DefaultCurrentSecurityContext(SecurityContextHolder.getContextHolderStrategy(), tenantResolver,
            extractors);
    }

    @Bean("tenantAuthorization")
    @ConditionalOnMissingBean(TenantAuthorization.class)
    TenantAuthorization tenantAuthorization(TenantResolver tenantResolver) {
        return new DefaultTenantAuthorization(tenantResolver);
    }

    @Bean
    @ConditionalOnMissingBean
    SecurityProblemDetailFactory securityProblemDetailFactory() {
        return new DefaultSecurityProblemDetailFactory();
    }

    @Bean
    @ConditionalOnBean(ObjectMapper.class)
    @ConditionalOnMissingBean(AuthenticationEntryPoint.class)
    AuthenticationEntryPoint securityAuthenticationEntryPoint(SecurityProblemDetailFactory factory,
                                                                ObjectMapper objectMapper) {
        return new ProblemDetailAuthenticationEntryPoint(factory, objectMapper);
    }

    @Bean
    @ConditionalOnBean(ObjectMapper.class)
    @ConditionalOnMissingBean(AccessDeniedHandler.class)
    AccessDeniedHandler securityAccessDeniedHandler(SecurityProblemDetailFactory factory, ObjectMapper objectMapper) {
        return new ProblemDetailAccessDeniedHandler(factory, objectMapper);
    }
}
