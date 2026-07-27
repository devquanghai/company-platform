package com.company.platform.logging.autoconfigure;

import com.company.platform.logging.autoconfigure.properties.PlatformLoggingProperties;
import com.company.platform.logging.support.PlatformLoggingPropertiesValidator;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

@AutoConfiguration
@EnableConfigurationProperties(PlatformLoggingProperties.class)
public class PlatformLoggingAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    PlatformLoggingPropertiesValidator platformLoggingPropertiesValidator(
        PlatformLoggingProperties properties, Environment environment,
        ConfigurableListableBeanFactory beans
    ) {
        return new PlatformLoggingPropertiesValidator(properties, environment, beans);
    }
}
