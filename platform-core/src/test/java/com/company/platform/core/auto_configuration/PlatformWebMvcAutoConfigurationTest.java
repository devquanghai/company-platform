package com.company.platform.core.auto_configuration;

import com.company.platform.core.config.web.PlatformWebMvcConfiguration;
import com.company.platform.core.config.web.PlatformStringFormatter;
import com.company.platform.core.configuration.properties.PlatformWebProperties;
import com.company.platform.core.auto_configuration.PlatformWebMvcAutoConfiguration;
import com.company.platform.core.web.filter.RequestResponseLoggingFilter;
import com.company.platform.core.web.filter.RequestCachingFilter;
import com.company.platform.core.web.filter.TraceContextFilter;
import com.company.platform.core.web.interceptor.RequestTimingInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.format.support.DefaultFormattingConversionService;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformWebMvcAutoConfigurationTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(PlatformWebMvcAutoConfiguration.class));

    @Test
    void registersRequestStringNormalization() throws Exception {
        runner.run(context -> {
            PlatformWebMvcConfiguration configuration =
                context.getBean(PlatformWebMvcConfiguration.class);
            assertThat(context).hasSingleBean(TraceContextFilter.class)
                .hasSingleBean(RequestTimingInterceptor.class)
                .doesNotHaveBean(RequestResponseLoggingFilter.class);
            DefaultFormattingConversionService registry = new DefaultFormattingConversionService();
            configuration.addFormatters(registry);
            configuration.addInterceptors(new InterceptorRegistry());
            assertThat(registry.convert("  value  ", String.class)).isEqualTo("value");
        });

        PlatformStringFormatter formatter = new PlatformStringFormatter();
        assertThat(formatter.parse(null, Locale.ENGLISH)).isNull();
        assertThat(formatter.parse(" text ", Locale.ENGLISH)).isEqualTo("text");
        assertThat(formatter.print("text", Locale.ENGLISH)).isEqualTo("text");
    }

    @Test
    void canDisableConfigurationAndNormalization() {
        runner.withPropertyValues("platform.core.web.enabled=false")
            .run(context -> assertThat(context).doesNotHaveBean(PlatformWebMvcConfiguration.class));
        runner.withPropertyValues("platform.core.web.trim-request-parameters=false")
            .run(context -> {
                PlatformWebProperties properties = context.getBean(PlatformWebProperties.class);
                PlatformWebMvcConfiguration configuration =
                    context.getBean(PlatformWebMvcConfiguration.class);
                DefaultFormattingConversionService registry = new DefaultFormattingConversionService();
                configuration.addFormatters(registry);
                assertThat(registry.convert("  value  ", String.class)).isEqualTo("  value  ");
                properties.setEnabled(false);
                properties.setTrimRequestParameters(true);
                properties.setTraceFilterEnabled(false);
                properties.setRequestLoggingEnabled(true);
                properties.setIncludePayload(true);
                properties.setMaxPayloadLength(1024);
                properties.setServerTimingEnabled(false);
                properties.setRequestCachingEnabled(true);
                properties.setMaxCachedRequestBodySize(2048);
                assertThat(properties.isEnabled()).isFalse();
                assertThat(properties.isTrimRequestParameters()).isTrue();
                assertThat(properties.isTraceFilterEnabled()).isFalse();
                assertThat(properties.isRequestLoggingEnabled()).isTrue();
                assertThat(properties.isIncludePayload()).isTrue();
                assertThat(properties.getMaxPayloadLength()).isEqualTo(1024);
                assertThat(properties.isServerTimingEnabled()).isFalse();
                assertThat(properties.isRequestCachingEnabled()).isTrue();
                assertThat(properties.getMaxCachedRequestBodySize()).isEqualTo(2048);
            });

        runner.withPropertyValues(
                "platform.core.web.request-logging-enabled=true",
                "platform.core.web.request-caching-enabled=true",
                "platform.core.web.trace-filter-enabled=false",
                "platform.core.web.server-timing-enabled=false")
            .run(context -> {
                assertThat(context).hasSingleBean(RequestResponseLoggingFilter.class)
                    .hasSingleBean(RequestCachingFilter.class)
                    .doesNotHaveBean(TraceContextFilter.class)
                    .doesNotHaveBean(RequestTimingInterceptor.class);
                context.getBean(PlatformWebMvcConfiguration.class)
                    .addInterceptors(new InterceptorRegistry());
            });
    }
}
