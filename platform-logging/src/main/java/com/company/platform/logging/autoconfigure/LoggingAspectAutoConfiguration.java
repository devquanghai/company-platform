package com.company.platform.logging.autoconfigure;

import com.company.platform.logging.api.crypto.CryptoService;
import com.company.platform.logging.api.logger.PlatformLogger;
import com.company.platform.logging.api.masking.DataMaskingService;
import com.company.platform.logging.application.resolver.LoggingAnnotationResolver;
import com.company.platform.logging.application.service.LoggingAnnotationAspect;
import com.company.platform.logging.autoconfigure.properties.PlatformLoggingProperties;
import com.company.platform.logging.crypto.annotation.CryptoAnnotationAspect;
import org.aspectj.lang.ProceedingJoinPoint;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(after = {
    StructuredLoggingAutoConfiguration.class, CryptoAutoConfiguration.class
})
@ConditionalOnClass(ProceedingJoinPoint.class)
public class LoggingAspectAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    LoggingAnnotationResolver loggingAnnotationResolver() {
        return new LoggingAnnotationResolver();
    }

    @Bean
    @ConditionalOnMissingBean
    LoggingAnnotationAspect loggingAnnotationAspect(
        LoggingAnnotationResolver resolver, PlatformLogger logger,
        DataMaskingService masking, PlatformLoggingProperties properties
    ) {
        return new LoggingAnnotationAspect(
            resolver, logger, masking, properties.getMethodLogging());
    }

    @Bean
    @ConditionalOnMissingBean
    CryptoAnnotationAspect cryptoAnnotationAspect(
        CryptoService crypto
    ) {
        return new CryptoAnnotationAspect(crypto);
    }
}
