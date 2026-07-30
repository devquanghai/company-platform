package com.company.platform.core.config.web;

import com.company.platform.core.web.interceptor.RequestTimingInterceptor;
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
