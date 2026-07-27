package com.company.platform.logging.autoconfigure;

import ch.qos.logback.classic.LoggerContext;
import com.company.platform.logging.logback.converter.BootstrapLogSanitizer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(after = MaskingAutoConfiguration.class)
@ConditionalOnClass(LoggerContext.class)
@ConditionalOnProperty(
    prefix = "platform.logging", name = "enabled",
    havingValue = "true", matchIfMissing = true)
public class LogbackAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    LogbackDefenseInDepth logbackDefenseInDepth() {
        return new LogbackDefenseInDepth();
    }

    public static final class LogbackDefenseInDepth {
        public String sanitize(String value) {
            return BootstrapLogSanitizer.sanitize(value);
        }
    }
}
