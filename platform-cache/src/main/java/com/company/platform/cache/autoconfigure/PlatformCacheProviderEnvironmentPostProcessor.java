package com.company.platform.cache.autoconfigure;

import com.company.platform.cache.domain.exception.PlatformCacheConfigurationException;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.Locale;
import java.util.Map;

public final class PlatformCacheProviderEnvironmentPostProcessor
    implements EnvironmentPostProcessor, Ordered {

    static final String PLATFORM_ENABLED = "platform.cache.enabled";
    static final String PLATFORM_PROVIDER = "platform.cache.provider";
    static final String SPRING_CACHE_TYPE = "spring.cache.type";

    @Override
    public void postProcessEnvironment(
        ConfigurableEnvironment environment,
        SpringApplication application
    ) {
        boolean enabled = environment.getProperty(PLATFORM_ENABLED, Boolean.class, true);
        String expected = enabled ? provider(environment) : "none";
        if (enabled && expected.equals("caffeine")) {
            validateCaffeineBound(environment);
        }
        String configured = environment.getProperty(SPRING_CACHE_TYPE);

        if (configured != null && !configured.isBlank()) {
            if (!configured.equalsIgnoreCase(expected)) {
                throw conflict(expected, configured);
            }
            return;
        }

        environment.getPropertySources().addLast(new MapPropertySource(
            "platformCacheProviderBridge", Map.of(SPRING_CACHE_TYPE, expected)));
    }

    private void validateCaffeineBound(ConfigurableEnvironment environment) {
        String spec = environment.getProperty("spring.cache.caffeine.spec");
        if (spec == null || spec.isBlank()) {
            environment.getPropertySources().addLast(new MapPropertySource(
                "platformCacheCaffeineDefaults",
                Map.of("spring.cache.caffeine.spec", "maximumSize=10000")));
            return;
        }
        if (!spec.contains("maximumSize=") && !spec.contains("maximumWeight=")) {
            throw new PlatformCacheConfigurationException(
                "Caffeine requires a bounded spring.cache.caffeine.spec with "
                    + "maximumSize or maximumWeight");
        }
    }

    private String provider(ConfigurableEnvironment environment) {
        String configured = environment.getProperty(PLATFORM_PROVIDER, "caffeine");
        String normalized = configured.trim().toLowerCase(Locale.ROOT);
        if (!normalized.equals("redis") && !normalized.equals("caffeine")) {
            throw new PlatformCacheConfigurationException(
                "platform.cache.provider must be REDIS or CAFFEINE");
        }
        return normalized;
    }

    private PlatformCacheConfigurationException conflict(
        String expected,
        String configured
    ) {
        String platformSelection = expected.equals("none")
            ? "platform.cache.enabled=false"
            : "platform.cache.provider=" + expected.toUpperCase(Locale.ROOT);
        return new PlatformCacheConfigurationException(
            platformSelection + " conflicts with spring.cache.type="
                + configured.toUpperCase(Locale.ROOT)
                + ". Use platform.cache.provider as the single provider selector.");
    }

    @Override
    public int getOrder() {
        return ConfigDataEnvironmentPostProcessor.ORDER + 1;
    }
}
