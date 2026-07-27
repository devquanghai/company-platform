package com.company.platform.logging.support;

import com.company.platform.logging.autoconfigure.properties.PlatformLoggingProperties;
import com.company.platform.logging.domain.exception.PlatformLoggingConfigurationException;
import com.company.platform.logging.domain.model.MaskingMatchType;
import com.company.platform.logging.domain.model.MaskingType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.mock.env.MockEnvironment;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValidatorBranchCoverageTest {

    @Test
    void acceptsDisabledRulesCustomBeansValidExpressionsAndEmergencyProductionMode() {
        PlatformLoggingProperties properties = new PlatformLoggingProperties();
        properties.getMasking().getRules().add(rule("disabled", false,
            MaskingMatchType.FIELD_NAME, List.of()));
        properties.getMasking().getRules().add(rule("field", true,
            MaskingMatchType.FIELD_NAME, List.of("email")));
        properties.getMasking().getRules().add(rule("json", true,
            MaskingMatchType.JSON_PATH, List.of("$.customer.cards[0].number")));
        PlatformLoggingProperties.MaskingRuleProperties regex =
            rule("regex", true, MaskingMatchType.REGEX, List.of("token=[A-Za-z0-9]+"));
        regex.setStrategyBean("customStrategy");
        properties.getMasking().getRules().add(regex);
        DefaultListableBeanFactory beans = new DefaultListableBeanFactory();
        beans.registerSingleton("customStrategy", new Object());

        assertThatCode(() -> validator(properties, new MockEnvironment(), beans).validate())
            .doesNotThrowAnyException();

        properties.setEnvironment(null);
        properties.getMasking().setEnabled(false);
        properties.getSecurity().setEmergencyAllowUnmaskedProduction(true);
        assertThatCode(() -> validator(properties, new MockEnvironment(), beans).validate())
            .doesNotThrowAnyException();
    }

    @Test
    void rejectsAllRemainingExpressionAndRuleValidationBranches() {
        invalid(properties -> properties.getMasking().getRules().add(
            rule("UPPER", true, MaskingMatchType.FIELD_NAME, List.of("field"))));
        invalid(properties -> properties.getMasking().getRules().add(
            rule("blank-expression", true, MaskingMatchType.FIELD_NAME, List.of(" "))));
        invalid(properties -> properties.getMasking().getRules().add(
            rule("bad-json", true, MaskingMatchType.JSON_PATH, List.of("$.items[abc]"))));
        invalid(properties -> properties.getMasking().getRules().add(
            rule("long-regex", true, MaskingMatchType.REGEX,
                List.of("a".repeat(1025)))));
        invalid(properties -> properties.getMasking().getRules().add(
            rule("invalid-regex", true, MaskingMatchType.REGEX, List.of("["))));
        invalid(properties -> properties.getMasking().getRules().add(
            rule("double-star", true, MaskingMatchType.REGEX, List.of(".*.*"))));
        invalid(properties -> {
            var rule = rule("negative-suffix", true,
                MaskingMatchType.FIELD_NAME, List.of("field"));
            rule.setVisibleSuffix(-1);
            properties.getMasking().getRules().add(rule);
        });
    }

    @Test
    void rejectsRemainingCryptoCacheAndSecureEnvironmentBranches() {
        invalid(properties -> properties.getCrypto().getProviders().getJca().setEnabled(false));
        invalid(properties -> properties.getCrypto().getKeyCache().setTtl(null));
        invalid(properties -> properties.getCrypto().getKeyCache()
            .setTtl(Duration.ofSeconds(-1)));
        invalid(properties -> properties.getCrypto().getKeyCache().setTtl(Duration.ZERO));
        invalid(properties -> {
            properties.setEnvironment("production");
            properties.getSecurity().setAllowWeakCrypto(true);
        });
        invalid(properties -> {
            properties.setEnvironment("production");
            properties.getContext().setUserIdMode(PlatformLoggingProperties.UserIdMode.HASH);
        });
        PlatformLoggingProperties secureHash = new PlatformLoggingProperties();
        secureHash.setEnvironment("production");
        secureHash.getContext().setUserIdMode(PlatformLoggingProperties.UserIdMode.HASH);
        secureHash.getMasking().setHmacKeyAlias("external-hmac");
        assertThatCode(() -> validator(secureHash, new MockEnvironment(),
            new DefaultListableBeanFactory()).validate()).doesNotThrowAnyException();
        invalid(properties -> {
            properties.setEnvironment("local");
            properties.getMasking().setEnabled(false);
        }, new MockEnvironment().withProperty("spring.profiles.active", "staging")
            .withProperty("unused", "value"), true);
    }

    @Test
    void rejectsRemainingOutputAndCorePayloadCombinations() {
        invalid(properties -> properties.getAsync().setFlushTimeout(null));
        invalid(properties -> properties.getAsync().setFlushTimeout(Duration.ofMillis(-1)));
        invalid(properties -> {
            properties.getFile().setEnabled(true);
            properties.getFile().setName(" ");
        });
        invalid(properties -> {
            properties.getFile().setEnabled(true);
            properties.getFile().setPath("\0");
        });

        PlatformLoggingProperties onlyRequestLogger = new PlatformLoggingProperties();
        assertThatCode(() -> validator(onlyRequestLogger,
            new MockEnvironment().withProperty(
                "platform.core.web.request-logging-enabled", "true"),
            new DefaultListableBeanFactory()).validate()).doesNotThrowAnyException();

        PlatformLoggingProperties onlyPayload = new PlatformLoggingProperties();
        assertThatCode(() -> validator(onlyPayload,
            new MockEnvironment().withProperty("platform.core.web.include-payload", "true"),
            new DefaultListableBeanFactory()).validate()).doesNotThrowAnyException();
    }

    private static PlatformLoggingProperties.MaskingRuleProperties rule(
        String name, boolean enabled, MaskingMatchType type, List<String> expressions
    ) {
        var rule = new PlatformLoggingProperties.MaskingRuleProperties();
        rule.setName(name);
        rule.setEnabled(enabled);
        rule.setMatchType(type);
        if (type == MaskingMatchType.JSON_PATH) {
            rule.getPaths().addAll(expressions);
        } else if (type == MaskingMatchType.REGEX) {
            rule.getPatterns().addAll(expressions);
        } else {
            rule.getFields().addAll(expressions);
        }
        return rule;
    }

    private static PlatformLoggingPropertiesValidator validator(
        PlatformLoggingProperties properties, MockEnvironment environment,
        DefaultListableBeanFactory beans
    ) {
        return new PlatformLoggingPropertiesValidator(properties, environment, beans);
    }

    private static void invalid(
        java.util.function.Consumer<PlatformLoggingProperties> mutation
    ) {
        invalid(mutation, new MockEnvironment(), false);
    }

    private static void invalid(
        java.util.function.Consumer<PlatformLoggingProperties> mutation,
        MockEnvironment environment, boolean activateStaging
    ) {
        if (activateStaging) {
            environment.setActiveProfiles("staging");
        }
        PlatformLoggingProperties properties = new PlatformLoggingProperties();
        mutation.accept(properties);
        assertThatThrownBy(() -> validator(
            properties, environment, new DefaultListableBeanFactory()).validate())
            .isInstanceOf(PlatformLoggingConfigurationException.class);
    }
}
