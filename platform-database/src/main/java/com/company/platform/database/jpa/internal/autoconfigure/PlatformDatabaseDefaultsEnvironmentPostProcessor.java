package com.company.platform.database.jpa.internal.autoconfigure;

import java.util.Map;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Adds secure, low-precedence defaults using only Spring Boot-owned property names.
 */
public final class PlatformDatabaseDefaultsEnvironmentPostProcessor implements EnvironmentPostProcessor {

    static final String PROPERTY_SOURCE_NAME = "platformDatabaseNativeDefaults";

    private static final Map<String, Object> DEFAULTS = Map.of(
            "spring.jpa.open-in-view", false,
            "spring.jpa.show-sql", false,
            "spring.jpa.hibernate.ddl-auto", "validate");

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (!environment.getPropertySources().contains(PROPERTY_SOURCE_NAME)) {
            environment.getPropertySources().addLast(new MapPropertySource(PROPERTY_SOURCE_NAME, DEFAULTS));
        }
    }
}
