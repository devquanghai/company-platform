package com.company.platform.core.auto_configuration;

import com.company.platform.core.context.MdcRequestContextProvider;
import com.company.platform.core.context.RequestContextProvider;
import com.company.platform.core.crypto.rsa.RsaService;
import com.company.platform.core.crypto.rsa.RsaServiceImpl;
import com.company.platform.core.i18n.I18nService;
import com.company.platform.core.i18n.DefaultI18nService;
import com.company.platform.core.configuration.properties.PlatformCoreI18nProperties;
import com.company.platform.core.exception.handler.PlatformAsyncExceptionHandler;
import com.company.platform.core.configuration.properties.PlatformCoreExceptionProperties;
import com.company.platform.core.exception.handler.PlatformExceptionHandler;
import com.company.platform.core.rest.factory.ResponseMetadataFactory;
import com.company.platform.core.rest.factory.ApiResponseFactory;
import com.company.platform.core.time.SystemTimeProvider;
import com.company.platform.core.time.TimeProvider;
import com.company.platform.core.trace.CurrentTraceContext;
import com.company.platform.core.trace.MicrometerTraceContextProvider;
import com.company.platform.core.trace.TraceContextProvider;
import io.micrometer.tracing.Tracer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;

import java.nio.charset.Charset;
import java.util.Locale;

@AutoConfiguration
@EnableConfigurationProperties({
    PlatformCoreI18nProperties.class,
    PlatformCoreExceptionProperties.class
})
public class PlatformCoreAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    TimeProvider platformTimeProvider() {
        return new SystemTimeProvider();
    }

    @Bean
    @ConditionalOnMissingBean
    RequestContextProvider platformRequestContextProvider() {
        return new MdcRequestContextProvider();
    }

    @Bean
    @ConditionalOnMissingBean
    TraceContextProvider platformTraceContextProvider(ObjectProvider<Tracer> tracer) {
        Tracer availableTracer = tracer.getIfAvailable();
        return availableTracer == null
            ? CurrentTraceContext::empty
            : new MicrometerTraceContextProvider(availableTracer);
    }

    @Bean
    @ConditionalOnMissingBean
    ResponseMetadataFactory responseMetadataFactory(RequestContextProvider requests,
                                                    TraceContextProvider traces,
                                                    TimeProvider time) {
        return new ResponseMetadataFactory(requests, traces, time);
    }

    @Bean
    @ConditionalOnMissingBean
    ApiResponseFactory apiResponseFactory(ResponseMetadataFactory metadataFactory) {
        return new ApiResponseFactory(metadataFactory);
    }

    @Bean
    @ConditionalOnMissingBean
    RsaService rsaService() {
        return new RsaServiceImpl();
    }

    @Bean
    @ConditionalOnMissingBean
    PlatformAsyncExceptionHandler platformAsyncExceptionHandler() {
        return new PlatformAsyncExceptionHandler();
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnProperty(
        prefix = "platform.core.exception-handling",
        name = "enabled",
        matchIfMissing = true
    )
    static class ExceptionHandlingConfiguration {
        @Bean
        @ConditionalOnMissingBean
        @ConditionalOnBean(I18nService.class)
        PlatformExceptionHandler platformExceptionHandler(
            ResponseMetadataFactory metadataFactory,
            PlatformCoreExceptionProperties properties,
            I18nService i18n
        ) {
            return new PlatformExceptionHandler(metadataFactory, properties, i18n);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "platform.core.i18n", name = "enabled", matchIfMissing = true)
    static class I18nConfiguration {
        @Bean("platformCoreMessageSource")
        @ConditionalOnMissingBean(name = "platformCoreMessageSource")
        MessageSource platformCoreMessageSource(PlatformCoreI18nProperties properties) {
            ResourceBundleMessageSource source = new ResourceBundleMessageSource();
            source.setBasenames(properties.getBasenames().toArray(String[]::new));
            source.setDefaultEncoding(Charset.forName(properties.getEncoding()).name());
            source.setDefaultLocale(Locale.forLanguageTag(properties.getDefaultLocale()));
            source.setFallbackToSystemLocale(properties.isFallbackToSystemLocale());
            source.setUseCodeAsDefaultMessage(properties.isUseCodeAsDefaultMessage());
            source.setCacheMillis(properties.getCacheDuration().toMillis());
            return source;
        }

        @Bean
        @ConditionalOnMissingBean(I18nService.class)
        I18nService i18nService(
            @Qualifier("platformCoreMessageSource") MessageSource platformCoreMessageSource
        ) {
            return new DefaultI18nService(platformCoreMessageSource);
        }
    }
}
