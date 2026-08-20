package com.company.platform.core.crypto.internal.autoconfigure;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JasyptSecurityPolicyEnvironmentPostProcessorTest {

    private final JasyptSecurityPolicyEnvironmentPostProcessor processor =
        new JasyptSecurityPolicyEnvironmentPostProcessor();

    @Test
    void suppliesStrongDefaultsAtLowPrecedence() {
        StandardEnvironment environment = new StandardEnvironment();

        processor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty(JasyptSecurityPolicyEnvironmentPostProcessor.ALGORITHM))
            .isEqualTo("PBEWITHHMACSHA512ANDAES_256");
        assertThat(environment.getProperty(
            JasyptSecurityPolicyEnvironmentPostProcessor.ITERATIONS, Integer.class))
            .isEqualTo(210_000);
        assertThat(environment.getPropertySources().get("platformCoreJasyptSecurityDefaults"))
            .isNotNull();
    }

    @Test
    void rejectsApplicationOverrideWithWeakAlgorithm() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource(
            "applicationConfiguration",
            Map.of(JasyptSecurityPolicyEnvironmentPostProcessor.ALGORITHM, "PBEWithMD5AndDES")));

        assertThatThrownBy(() -> processor.postProcessEnvironment(
            environment, new SpringApplication()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Unsafe Jasypt configuration rejected for property "
                + JasyptSecurityPolicyEnvironmentPostProcessor.ALGORITHM);
    }
}
