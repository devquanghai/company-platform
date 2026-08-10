package com.company.platform.core.web.internal.configuration;

import com.company.platform.core.web.internal.adapter.servlet.RequestTimingInterceptor;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

public final class PlatformWebMvcConfiguration implements WebMvcConfigurer {

    private final RequestTimingInterceptor timingInterceptor;

    public PlatformWebMvcConfiguration(
        RequestTimingInterceptor timingInterceptor
    ) {
        this.timingInterceptor = timingInterceptor;
    }

    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addFormatter(new PlatformStringFormatter());
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        if (timingInterceptor != null) {
            registry.addInterceptor(timingInterceptor);
        }
    }
}
