package com.company.platform.core.auto_configuration;

import com.company.platform.core.audit.AuditAspect;
import com.company.platform.core.audit.AuditChangeResolver;
import com.company.platform.core.audit.DefaultAuditChangeResolver;
import com.company.platform.core.audit.jpa.SecurityContextAuditorAware;
import com.company.platform.core.audit.jpa.SystemAuditorAware;
import com.company.platform.core.config.audit.PlatformAuditingConfiguration;
import com.company.platform.core.configuration.properties.PlatformAuditProperties;
import com.company.platform.core.auto_configuration.PlatformCoreAutoConfiguration;
import com.company.platform.core.context.RequestContextProvider;
import com.company.platform.core.time.TimeProvider;
import com.company.platform.core.trace.TraceContextProvider;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.AuditorAware;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.security.core.context.SecurityContextHolder;

@AutoConfiguration(after = PlatformCoreAutoConfiguration.class)
@ConditionalOnClass(AuditorAware.class)
@ConditionalOnProperty(prefix = "platform.core.audit", name = "enabled")
@EnableConfigurationProperties(PlatformAuditProperties.class)
public class PlatformAuditAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    AuditChangeResolver platformAuditChangeResolver() {
        return new DefaultAuditChangeResolver();
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(SecurityContextHolder.class)
    static class SecurityAuditorConfiguration {
        @Bean
        @ConditionalOnMissingBean(AuditorAware.class)
        AuditorAware<String> platformAuditorAware(PlatformAuditProperties properties) {
            return new SecurityContextAuditorAware(properties.getDefaultAuditor());
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnMissingClass("org.springframework.security.core.context.SecurityContextHolder")
    static class SystemAuditorConfiguration {
        @Bean
        @ConditionalOnMissingBean(AuditorAware.class)
        AuditorAware<String> platformAuditorAware(PlatformAuditProperties properties) {
            return new SystemAuditorAware(properties.getDefaultAuditor());
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = {
        "jakarta.persistence.EntityManagerFactory",
        "org.springframework.data.jpa.domain.support.AuditingEntityListener"
    })
    @ConditionalOnBean(EntityManagerFactory.class)
    @Import(PlatformAuditingConfiguration.class)
    static class JpaAuditingConfiguration {
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(Aspect.class)
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    static class AuditAspectConfiguration {
        @Bean
        @ConditionalOnMissingBean
        AuditAspect platformAuditAspect(
            AuditorAware<String> auditorAware,
            AuditChangeResolver changeResolver,
            ApplicationEventPublisher eventPublisher,
            RequestContextProvider requestContextProvider,
            TraceContextProvider traceContextProvider,
            TimeProvider timeProvider,
            PlatformAuditProperties properties
        ) {
            return new AuditAspect(
                auditorAware,
                changeResolver,
                eventPublisher,
                requestContextProvider,
                traceContextProvider,
                timeProvider,
                properties
            );
        }
    }
}
