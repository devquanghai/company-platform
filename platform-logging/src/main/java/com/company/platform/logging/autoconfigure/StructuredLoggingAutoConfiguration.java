package com.company.platform.logging.autoconfigure;

import com.company.platform.core.context.CurrentUserProvider;
import com.company.platform.core.context.RequestContextProvider;
import com.company.platform.core.time.TimeProvider;
import com.company.platform.core.trace.TraceContextProvider;
import com.company.platform.logging.api.logger.PlatformLogger;
import com.company.platform.logging.api.masking.DataMaskingService;
import com.company.platform.logging.api.masking.MaskingHashKeyProvider;
import com.company.platform.logging.application.service.DefaultPlatformLogger;
import com.company.platform.logging.audit.publisher.LoggingAuditEventPublisher;
import com.company.platform.logging.autoconfigure.properties.PlatformLoggingProperties;
import com.company.platform.logging.observability.metrics.LoggingMetrics;
import com.company.platform.logging.structured.customizer.PlatformLogEventCustomizer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(after = {
    MaskingAutoConfiguration.class,
    LoggingAuditAutoConfiguration.class,
    LoggingMetricsAutoConfiguration.class
})
@ConditionalOnProperty(
    prefix = "platform.logging", name = "enabled",
    havingValue = "true", matchIfMissing = true)
public class StructuredLoggingAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(PlatformLogger.class)
    DefaultPlatformLogger platformLogger(
        DataMaskingService masking, RequestContextProvider requests,
        TraceContextProvider traces, TimeProvider time,
        ObjectProvider<CurrentUserProvider> users,
        ObjectProvider<PlatformLogEventCustomizer> customizers,
        ObjectProvider<LoggingAuditEventPublisher> audit,
        ObjectProvider<LoggingMetrics> metrics,
        ObjectProvider<MaskingHashKeyProvider> hashKeys,
        PlatformLoggingProperties properties
    ) {
        return new DefaultPlatformLogger(
            masking, requests, traces, time, users.getIfAvailable(),
            customizers.orderedStream().toList(), audit.getIfAvailable(),
            metrics.getIfAvailable(), properties.getAudit().getFailMode(),
            properties.getContext().getUserIdMode(), hashKeys.getIfAvailable(),
            properties.getMasking().getHmacKeyAlias());
    }
}
