package com.company.platform.core.crypto.internal.autoconfigure;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.Locale;
import java.util.Map;

public final class JasyptSecurityPolicyEnvironmentPostProcessor
    implements EnvironmentPostProcessor, Ordered {

    static final String ALGORITHM = "jasypt.encryptor.algorithm";
    static final String ITERATIONS = "jasypt.encryptor.key-obtention-iterations";
    static final String SALT_GENERATOR = "jasypt.encryptor.salt-generator-classname";
    static final String IV_GENERATOR = "jasypt.encryptor.iv-generator-classname";

    private static final String REQUIRED_ALGORITHM = "PBEWITHHMACSHA512ANDAES_256";
    private static final int MINIMUM_ITERATIONS = 210_000;
    private static final String REQUIRED_SALT_GENERATOR = "org.jasypt.salt.RandomSaltGenerator";
    private static final String REQUIRED_IV_GENERATOR = "org.jasypt.iv.RandomIvGenerator";

    @Override
    public void postProcessEnvironment(
        ConfigurableEnvironment environment,
        SpringApplication application
    ) {
        validateAlgorithm(environment.getProperty(ALGORITHM, REQUIRED_ALGORITHM));
        validateIterations(environment.getProperty(ITERATIONS, Integer.class, MINIMUM_ITERATIONS));
        validateClassName(SALT_GENERATOR,
            environment.getProperty(SALT_GENERATOR, REQUIRED_SALT_GENERATOR),
            REQUIRED_SALT_GENERATOR);
        validateClassName(IV_GENERATOR,
            environment.getProperty(IV_GENERATOR, REQUIRED_IV_GENERATOR),
            REQUIRED_IV_GENERATOR);

        environment.getPropertySources().addLast(new MapPropertySource(
            "platformCoreJasyptSecurityDefaults",
            Map.of(
                ALGORITHM, REQUIRED_ALGORITHM,
                ITERATIONS, MINIMUM_ITERATIONS,
                SALT_GENERATOR, REQUIRED_SALT_GENERATOR,
                IV_GENERATOR, REQUIRED_IV_GENERATOR)));
    }

    private static void validateAlgorithm(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!REQUIRED_ALGORITHM.equals(normalized)) {
            throw invalid(ALGORITHM);
        }
    }

    private static void validateIterations(Integer value) {
        if (value == null || value < MINIMUM_ITERATIONS) {
            throw invalid(ITERATIONS);
        }
    }

    private static void validateClassName(String property, String value, String required) {
        if (!required.equals(value)) {
            throw invalid(property);
        }
    }

    private static IllegalStateException invalid(String property) {
        return new IllegalStateException(
            "Unsafe Jasypt configuration rejected for property " + property);
    }

    @Override
    public int getOrder() {
        return ConfigDataEnvironmentPostProcessor.ORDER + 1;
    }
}
