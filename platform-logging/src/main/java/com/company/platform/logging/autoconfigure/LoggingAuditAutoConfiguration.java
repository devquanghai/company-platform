package com.company.platform.logging.autoconfigure;

import com.company.platform.logging.audit.publisher.LoggingAuditEventPublisher;
import com.company.platform.logging.audit.publisher.SpringLoggingAuditEventPublisher;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(after = PlatformLoggingAutoConfiguration.class)
@ConditionalOnExpression(
    "${platform.logging.enabled:true} and ${platform.logging.audit.enabled:true}")
public class LoggingAuditAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(
        prefix = "platform.logging.audit", name = "publish-spring-event",
        havingValue = "true", matchIfMissing = true)
    LoggingAuditEventPublisher loggingAuditEventPublisher(
        ApplicationEventPublisher publisher
    ) {
        return new SpringLoggingAuditEventPublisher(publisher);
    }
}
