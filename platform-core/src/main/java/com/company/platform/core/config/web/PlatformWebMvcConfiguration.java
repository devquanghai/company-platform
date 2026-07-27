package com.company.platform.core.config.web;

import com.company.platform.core.config.web.PlatformStringFormatter;
import com.company.platform.core.configuration.properties.PlatformWebProperties;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import com.company.platform.core.web.interceptor.RequestTimingInterceptor;

public final class PlatformWebMvcConfiguration implements WebMvcConfigurer {

    private final PlatformWebProperties properties;
    private final RequestTimingInterceptor timingInterceptor;

    public PlatformWebMvcConfiguration(
        PlatformWebProperties properties,
        RequestTimingInterceptor timingInterceptor
    ) {
        this.properties = properties;
        this.timingInterceptor = timingInterceptor;
    }

    @Override
    public void addFormatters(FormatterRegistry registry) {
        if (properties.isTrimRequestParameters()) {
            registry.addFormatter(new PlatformStringFormatter());
        }
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        if (timingInterceptor != null) {
            registry.addInterceptor(timingInterceptor);
        }
    }
}
