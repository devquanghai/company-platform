package com.company.platform.logging.context;

import com.company.platform.logging.autoconfigure.properties.PlatformLoggingProperties;
import com.company.platform.logging.domain.exception.PlatformLoggingConfigurationException;
import com.company.platform.logging.logback.converter.BootstrapLogSanitizer;
import com.company.platform.logging.logback.converter.MaskingKeyValueConverter;
import com.company.platform.logging.logback.converter.MaskingMdcConverter;
import com.company.platform.logging.logback.converter.MaskingMessageConverter;
import com.company.platform.logging.logback.converter.SanitizedThrowableProxyConverter;
import com.company.platform.logging.support.PlatformLoggingPropertiesValidator;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import org.junit.jupiter.api.Test;
import org.slf4j.event.KeyValuePair;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.mock.env.MockEnvironment;

import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContextAndConfigurationTest {


    @Test
    void bootstrapSanitizerMasksSecretKeysControlsObjectsAndBoundsOutput() {
        assertThat(BootstrapLogSanitizer.sanitize((String) null)).isEmpty();
        assertThat(BootstrapLogSanitizer.sanitize(
            "password=secret\napi-key:token\tmessage"))
            .doesNotContain("secret", "token", "\n", "\t")
            .contains("password=***", "api-key=***");
        assertThat(BootstrapLogSanitizer.sanitize("Authorization", "Bearer raw"))
            .isEqualTo("***");
        assertThat(BootstrapLogSanitizer.sanitize("normal", 42)).isEqualTo("42");
        assertThat(BootstrapLogSanitizer.sanitize("normal", new Object()))
            .isEqualTo("<object-not-logged>");
        assertThat(BootstrapLogSanitizer.sanitize("x".repeat(20_000))).hasSize(16_384);
    }

    @Test
    void logbackConvertersSanitizeEveryOutputBoundary() {
        LoggingEvent event = new LoggingEvent();
        event.setMessage("password=raw\nsafe");
        event.setMDCPropertyMap(Map.of(
            "authorization", "Bearer raw", "traceId", "trace-1"));
        event.addKeyValuePair(new KeyValuePair("api-key", "raw-key"));
        event.addKeyValuePair(new KeyValuePair("count", 3));

        assertThat(new MaskingMessageConverter().convert(event))
            .doesNotContain("raw", "\n").contains("password=***");
        assertThat(new MaskingMdcConverter().convert(event))
            .contains("authorization=***", "traceId=trace-1").doesNotContain("Bearer");
        assertThat(new MaskingKeyValueConverter().convert(event))
            .contains("api-key=***", "count=3").doesNotContain("raw-key");
        assertThat(new MaskingMessageConverter().convert(null)).isEmpty();
        assertThat(new MaskingMdcConverter().convert(null)).isEmpty();
        assertThat(new MaskingKeyValueConverter().convert(null)).isEmpty();

        LoggingEvent failure = new LoggingEvent();
        failure.setThrowableProxy(
            new ThrowableProxy(new IllegalStateException("password=raw-secret")));
        String converted = new SanitizedThrowableProxyConverter().convert(failure);
        assertThat(converted).doesNotContain("raw-secret").contains("password=***");
    }

    @Test
    void validatorAcceptsDefaults() {
        assertThatCode(() -> validator(new PlatformLoggingProperties(),
            new MockEnvironment(), new DefaultListableBeanFactory()).validate())
            .doesNotThrowAnyException();
    }

    @Test
    void validatorRejectsUnsafeMaskingRulesAndProductionOverrides() {
        invalid(properties -> addRule(properties, null, "field"));
        invalid(properties -> {
            addRule(properties, "duplicate", "field");
            addRule(properties, "duplicate", "other");
        });
        invalid(properties -> {
            addRule(properties, "partial", "field");
            properties.getMasking().getRules().getFirst().setVisiblePrefix(-1);
        });
        invalid(properties -> {
            addRule(properties, "blank-substitution", "field");
            properties.getMasking().getRules().getFirst().setSubstitution(" ");
        });
        invalid(properties -> {
            addRule(properties, "empty-expression", "field");
            properties.getMasking().getRules().getFirst().getFields().clear();
        });
        invalid(properties -> {
            addRule(properties, "bad-json-path", "field");
            var rule = properties.getMasking().getRules().getFirst();
            rule.setMatchType(com.company.platform.logging.domain.model.MaskingMatchType.JSON_PATH);
            rule.setPaths(new java.util.ArrayList<>(java.util.List.of("customer.secret")));
        });
        invalid(properties -> {
            addRule(properties, "bad-regex", "field");
            var rule = properties.getMasking().getRules().getFirst();
            rule.setMatchType(com.company.platform.logging.domain.model.MaskingMatchType.REGEX);
            rule.setPatterns(new java.util.ArrayList<>(java.util.List.of("(a+)+")));
        });
        invalid(properties -> {
            addRule(properties, "missing-bean", "field");
            properties.getMasking().getRules().getFirst().setStrategyBean("missingStrategy");
        });
        invalid(properties -> {
            properties.setEnvironment("prod");
            addRule(properties, "hash", "field");
            properties.getMasking().getRules().getFirst()
                .setMaskingType(com.company.platform.logging.domain.model.MaskingType.HASH);
        });
    }

    @Test
    void validatorRejectsUnsafeCryptoOutputAndRawPayloadCombinations() {
        invalid(properties -> properties.getCrypto().getDefaults().setKeyAlias(" "));
        invalid(properties -> {
            properties.setEnvironment("prod");
            properties.getCrypto().setAllowLegacyAlgorithms(true);
        });
        invalid(properties -> properties.getCrypto().getProviders().getJasypt().setEnabled(true));
        PlatformLoggingProperties properties = new PlatformLoggingProperties();
        MockEnvironment environment = new MockEnvironment()
            .withProperty("platform.core.web.request-logging-enabled", "true")
            .withProperty("platform.core.web.include-payload", "true");
        assertThatThrownBy(() -> validator(properties, environment,
            new DefaultListableBeanFactory()).validate())
            .isInstanceOf(PlatformLoggingConfigurationException.class);
    }

    private static PlatformLoggingPropertiesValidator validator(
        PlatformLoggingProperties properties, MockEnvironment environment,
        DefaultListableBeanFactory beans
    ) {
        return new PlatformLoggingPropertiesValidator(properties, environment, beans);
    }

    private static void invalid(Consumer<PlatformLoggingProperties> mutation) {
        PlatformLoggingProperties properties = new PlatformLoggingProperties();
        mutation.accept(properties);
        assertThatThrownBy(() -> validator(properties, new MockEnvironment(),
            new DefaultListableBeanFactory()).afterSingletonsInstantiated())
            .isInstanceOf(PlatformLoggingConfigurationException.class);
    }

    private static void addRule(
        PlatformLoggingProperties properties, String name, String field
    ) {
        var rule = new PlatformLoggingProperties.MaskingRuleProperties();
        rule.setName(name);
        rule.getFields().add(field);
        properties.getMasking().getRules().add(rule);
    }

}
