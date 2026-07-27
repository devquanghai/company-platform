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
                .hasSingleBean(LogbackAutoConfiguration.LogbackDefenseInDepth.class);
            assertThat(context.getBeansOfType(MaskingStrategy.class)).hasSize(5);
            assertThat(context.getBean(DataMaskingService.class)
                .maskValue("password", "secret")).isEqualTo("***");
            assertThat(context.getBean(LogbackAutoConfiguration.LogbackDefenseInDepth.class)
                .sanitize("api-key=secret")).isEqualTo("api-key=***");
        });
    }

    @Test
    void disabledModuleOnlyKeepsPropertiesAndValidator() {
        runner.withPropertyValues("platform.logging.enabled=false").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(PlatformLoggingProperties.class)
                .hasSingleBean(com.company.platform.logging.support
                    .PlatformLoggingPropertiesValidator.class)
                .doesNotHaveBean(DataMaskingService.class)
                .doesNotHaveBean(MaskingStrategyRegistry.class)
                .doesNotHaveBean(LogbackAutoConfiguration.LogbackDefenseInDepth.class);
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
            "platform.logging.async.flush-timeout=0s"
        ).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasMessageContaining("flush timeout");
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
