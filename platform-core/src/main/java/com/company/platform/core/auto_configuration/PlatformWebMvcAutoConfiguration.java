package com.company.platform.core.auto_configuration;

import com.company.platform.core.web.internal.configuration.PlatformWebMvcConfiguration;
import com.company.platform.core.web.internal.configuration.PlatformApiResponseBodyAdvice;
import com.company.platform.core.configuration.properties.PlatformWebProperties;
import com.company.platform.core.rest.factory.ResponseMetadataFactory;
import com.company.platform.core.web.internal.adapter.servlet.RequestResponseLoggingFilter;
import com.company.platform.core.web.internal.adapter.servlet.RequestCachingFilter;
import com.company.platform.core.web.internal.adapter.servlet.TraceContextFilter;
import com.company.platform.core.web.internal.adapter.servlet.RequestTimingInterceptor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.servlet.DispatcherServlet;

@AutoConfiguration
@ConditionalOnClass(DispatcherServlet.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "platform.core.web", name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties(PlatformWebProperties.class)
public class PlatformWebMvcAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(
        prefix = "platform.core.web",
        name = "trace-filter-enabled",
        matchIfMissing = true
    )
    TraceContextFilter platformTraceContextFilter() {
        return new TraceContextFilter();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "platform.core.web", name = "request-caching-enabled")
    RequestCachingFilter platformRequestCachingFilter(PlatformWebProperties properties) {
        return new RequestCachingFilter(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "platform.core.web", name = "request-logging-enabled")
    RequestResponseLoggingFilter platformRequestResponseLoggingFilter(
        PlatformWebProperties properties
    ) {
        return new RequestResponseLoggingFilter(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(
        prefix = "platform.core.web",
        name = "server-timing-enabled",
        matchIfMissing = true
    )
    RequestTimingInterceptor platformRequestTimingInterceptor() {
        return new RequestTimingInterceptor(System::nanoTime);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(ResponseMetadataFactory.class)
    @ConditionalOnProperty(
        prefix = "platform.core.web",
        name = "response-metadata-enabled",
        matchIfMissing = true
    )
    PlatformApiResponseBodyAdvice platformApiResponseBodyAdvice(
        ResponseMetadataFactory metadataFactory
    ) {
        return new PlatformApiResponseBodyAdvice(metadataFactory);
    }

    @Bean
    @ConditionalOnMissingBean(PlatformWebMvcConfiguration.class)
    PlatformWebMvcConfiguration platformWebMvcConfiguration(
        PlatformWebProperties properties,
        ObjectProvider<RequestTimingInterceptor> timingInterceptor
    ) {
        return new PlatformWebMvcConfiguration(timingInterceptor.getIfAvailable());
    }
}
