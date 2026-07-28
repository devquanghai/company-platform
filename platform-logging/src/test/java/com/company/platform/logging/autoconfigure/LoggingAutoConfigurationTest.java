package com.company.platform.logging.autoconfigure;

import com.company.platform.core.json.JsonMapperHelper;
import com.company.platform.logging.api.masking.DataMaskingService;
import com.company.platform.logging.api.masking.MaskingStrategy;
import com.company.platform.logging.api.masking.MaskingStrategyRegistry;
import com.company.platform.logging.autoconfigure.properties.PlatformLoggingProperties;
import com.company.platform.logging.logback.converter.BootstrapLogSanitizer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LoggingAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(
            PlatformLoggingAutoConfiguration.class,
            MaskingAutoConfiguration.class,
            LogbackAutoConfiguration.class))
        .withUserConfiguration(CoreJsonConfiguration.class);

    @Test
    void createsDefaultMaskingContextAndLogbackBeans() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(PlatformLoggingProperties.class)
                .hasSingleBean(DataMaskingService.class)
                .hasSingleBean(MaskingStrategyRegistry.class)
                .hasSingleBean(com.company.platform.logging.logback.converter
                    .LogbackMaskingLifecycle.class)
                .hasSingleBean(LogbackAutoConfiguration.LogbackDefenseInDepth.class);
            assertThat(context.getBeansOfType(MaskingStrategy.class)).hasSize(5);
            assertThat(context.getBean(DataMaskingService.class)
                .maskValue("password", "secret")).isEqualTo("***");
            assertThat(context.getBean(LogbackAutoConfiguration.LogbackDefenseInDepth.class)
                .sanitize("api-key=secret")).isEqualTo("api-key=***");
        });
    }

    @Test
    void applicationRulesAreInstalledIntoLogbackConverters() {
        runner.withPropertyValues(
            "platform.logging.masking.rules[0].name=integration-email",
            "platform.logging.masking.rules[0].match-type=FIELD_NAME",
            "platform.logging.masking.rules[0].fields[0]=email",
            "platform.logging.masking.rules[0].pii-type=EMAIL",
            "platform.logging.masking.rules[0].masking-type=PARTIAL",
            "platform.logging.masking.rules[0].visible-prefix=2",
            "platform.logging.masking.rules[0].preserve-domain=true",
            "platform.logging.masking.rules[1].name=password",
            "platform.logging.masking.rules[1].match-type=FIELD_NAME",
            "platform.logging.masking.rules[1].fields[0]=password",
            "platform.logging.masking.rules[1].pii-type=PASSWORD",
            "platform.logging.masking.rules[1].masking-type=FULL",
            "platform.logging.masking.rules[2].name=date-of-birth",
            "platform.logging.masking.rules[2].match-type=FIELD_NAME",
            "platform.logging.masking.rules[2].fields[0]=dateOfBirth",
            "platform.logging.masking.rules[2].pii-type=DATE_OF_BIRTH",
            "platform.logging.masking.rules[2].masking-type=PARTIAL",
            "platform.logging.masking.rules[2].visible-prefix=4"
        ).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(BootstrapLogSanitizer.sanitize(
                "email", "alice@example.org"))
                .isEqualTo("al***@example.org");
            assertThat(BootstrapLogSanitizer.sanitize(
                "email=alice@example.org password=raw-secret "
                    + "dateOfBirth=2000-01-02"))
                .contains("email=al***@example.org")
                .contains("password=***")
                .contains("dateOfBirth=2000******")
                .doesNotContain("alice@example.org", "raw-secret", "2000-01-02");
            assertThat(BootstrapLogSanitizer.sanitize(
                "{\"email\":\"alice@example.org\","
                    + "\"dateOfBirth\":\"2000-01-02\"}"))
                .contains("\"email\":\"al***@example.org\"")
                .contains("\"dateOfBirth\":\"2000******\"");
        });
    }

    @Test
    void customMaskingServiceBacksOffWhileStrategiesRemainExtensible() {
        runner.withUserConfiguration(CustomMaskingConfiguration.class).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(DataMaskingService.class);
            assertThat(context.getBean(DataMaskingService.class).maskValue("x", "y"))
                .isEqualTo("consumer-y");
        });
    }

    @Test
    void invalidBoundPropertiesFailContextStartup() {
        runner.withPropertyValues(
            "platform.logging.masking.max-depth=0"
        ).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                .hasStackTraceContaining("masking.maxDepth");
        });
    }

    @Configuration(proxyBeanMethods = false)
    static class CoreJsonConfiguration {
        @Bean
        JsonMapperHelper jsonMapperHelper() {
            return new JsonMapperHelper(JsonMapper.builder().build());
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomMaskingConfiguration {
        @Bean
        DataMaskingService consumerMaskingService() {
            return new DataMaskingService() {
                @Override public String maskValue(String fieldName, String value) {
                    return "consumer-" + value;
                }
                @Override public Object sanitize(Object source) { return source; }
                @Override public String sanitizeJson(String source) { return source; }
                @Override public String sanitizeMessage(String message) { return message; }
                @Override public Map<String, Object> sanitizeFields(Map<String, ?> fields) {
                    return Map.copyOf(fields);
                }
                @Override public com.company.platform.logging.domain.model.SanitizedThrowable
                sanitizeThrowable(Throwable throwable) {
                    return null;
                }
            };
        }
    }
}
