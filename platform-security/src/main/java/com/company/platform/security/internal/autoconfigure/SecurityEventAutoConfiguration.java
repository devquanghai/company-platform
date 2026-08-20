package com.company.platform.security.internal.autoconfigure;

import com.company.platform.security.event.api.SecurityAuditEventPublisher;
import com.company.platform.security.event.internal.SpringSecurityEventAdapter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(after = PlatformSecurityAutoConfiguration.class)
@ConditionalOnBean(SecurityAuditEventPublisher.class)
public class SecurityEventAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    SpringSecurityEventAdapter springSecurityEventAdapter(SecurityAuditEventPublisher publisher) {
        return new SpringSecurityEventAdapter(publisher);
    }
}
