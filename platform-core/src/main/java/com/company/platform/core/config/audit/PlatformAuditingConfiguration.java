package com.company.platform.core.config.audit;

import com.company.platform.core.configuration.properties.PlatformAuditProperties;
import com.company.platform.core.time.TimeProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.time.ZoneId;
import java.util.Optional;

/**
 * Cấu hình JPA auditing sử dụng auditor và thời gian do platform quản lý.
 */
@Configuration(proxyBeanMethods = false)
@EnableJpaAuditing(
    auditorAwareRef = "platformAuditorAware",
    dateTimeProviderRef = "platformAuditingDateTimeProvider"
)
public class PlatformAuditingConfiguration {

    @Bean("platformAuditingDateTimeProvider")
    @ConditionalOnMissingBean(name = "platformAuditingDateTimeProvider")
    public DateTimeProvider platformAuditingDateTimeProvider(
        TimeProvider timeProvider,
        PlatformAuditProperties properties
    ) {
        ZoneId zoneId = ZoneId.of(properties.getTimezone());
        return () -> Optional.of(timeProvider.now(zoneId));
    }
}
