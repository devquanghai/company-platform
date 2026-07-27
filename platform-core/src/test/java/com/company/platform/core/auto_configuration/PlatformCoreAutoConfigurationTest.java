package com.company.platform.core.auto_configuration;

import com.company.platform.core.auto_configuration.PlatformCoreAutoConfiguration;
import com.company.platform.core.context.RequestContextProvider;
import com.company.platform.core.crypto.rsa.RsaService;
import com.company.platform.core.i18n.I18nService;
import com.company.platform.core.configuration.properties.PlatformCoreI18nProperties;
import com.company.platform.core.exception.handler.PlatformAsyncExceptionHandler;
import com.company.platform.core.exception.handler.PlatformExceptionHandler;
import com.company.platform.core.rest.factory.ResponseMetadataFactory;
import com.company.platform.core.rest.factory.ApiResponseFactory;
import com.company.platform.core.time.TimeProvider;
import com.company.platform.core.trace.TraceContextProvider;
import com.company.platform.core.trace.MicrometerTraceContextProvider;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformCoreAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(PlatformCoreAutoConfiguration.class));
    private final WebApplicationContextRunner webRunner = new WebApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(PlatformCoreAutoConfiguration.class));

    @Test
    void suppliesEnterpriseDefaultsAndBindsI18nProperties() {
        runner.withPropertyValues(
                "platform.core.i18n.default-locale=vi",
                "platform.core.i18n.cache-duration=30s",
                "platform.core.i18n.basenames=messages,core_message")
            .run(context -> {
                assertThat(context).hasSingleBean(TimeProvider.class)
                    .hasSingleBean(RequestContextProvider.class)
                    .hasSingleBean(TraceContextProvider.class)
                    .hasSingleBean(ResponseMetadataFactory.class)
                    .hasSingleBean(ApiResponseFactory.class)
                    .hasSingleBean(RsaService.class)
                    .hasSingleBean(PlatformAsyncExceptionHandler.class)
                    .hasSingleBean(I18nService.class);
                PlatformCoreI18nProperties properties = context.getBean(PlatformCoreI18nProperties.class);
                assertThat(properties.getDefaultLocale()).isEqualTo("vi");
                assertThat(properties.getCacheDuration()).hasSeconds(30);
                assertThat(properties.getBasenames()).containsExactly("messages", "core_message");
            });
    }

    @Test
    void canDisableI18n() {
        runner.withPropertyValues("platform.core.i18n.enabled=false")
            .run(context -> assertThat(context).doesNotHaveBean(I18nService.class));
    }

    @Test
    void backsOffForConsumerBeans() {
        runner.withUserConfiguration(UserBeans.class)
            .run(context -> {
                assertThat(context).hasSingleBean(TimeProvider.class);
                assertThat(context.getBean(TimeProvider.class)).isSameAs(UserBeans.TIME);
                assertThat(context).hasSingleBean(TraceContextProvider.class);
            });
    }

    @Test
    void configuresExceptionHandlerOnlyForEnabledServletApplications() {
        webRunner.run(context -> assertThat(context).hasSingleBean(PlatformExceptionHandler.class));
        webRunner.withPropertyValues("platform.core.exception-handling.enabled=false")
            .run(context -> assertThat(context).doesNotHaveBean(PlatformExceptionHandler.class));
    }

    @Test
    void usesMicrometerTraceProviderWhenTracerExists() {
        runner.withUserConfiguration(TracerConfiguration.class)
            .run(context -> assertThat(context.getBean(TraceContextProvider.class))
                .isInstanceOf(MicrometerTraceContextProvider.class));
    }

    @Configuration(proxyBeanMethods = false)
    static class UserBeans {
        static final TimeProvider TIME = new com.company.platform.core.time.SystemTimeProvider();
        @Bean TimeProvider customTimeProvider() { return TIME; }
    }

    @Configuration(proxyBeanMethods = false)
    static class TracerConfiguration {
        @Bean Tracer tracer() { return Tracer.NOOP; }
    }
}
