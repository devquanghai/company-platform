package com.company.platform.logging.support;

import com.company.platform.logging.autoconfigure.properties.PlatformLoggingProperties;
import com.company.platform.logging.domain.exception.PlatformLoggingConfigurationException;
import com.company.platform.logging.domain.model.MaskingMatchType;
import com.company.platform.logging.domain.model.MaskingType;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.env.Environment;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public final class PlatformLoggingPropertiesValidator
    implements SmartInitializingSingleton {

    private static final Pattern RULE_NAME = Pattern.compile("[a-z0-9][a-z0-9._-]*");
    private final PlatformLoggingProperties properties;
    private final Environment environment;
    private final ConfigurableListableBeanFactory beans;

    public PlatformLoggingPropertiesValidator(
        PlatformLoggingProperties properties, Environment environment,
        ConfigurableListableBeanFactory beans
    ) {
        this.properties = properties;
        this.environment = environment;
        this.beans = beans;
    }

    @Override
    public void afterSingletonsInstantiated() {
        validate();
    }

    public void validate() {
        validateMasking();
        validateCrypto();
        rejectRawCorePayloadLogger();
    }

    private void validateMasking() {
        boolean secure = isSecureEnvironment();
        if (secure
            && properties.getContext().getUserIdMode()
                == PlatformLoggingProperties.UserIdMode.HASH
            && !StringUtils.hasText(properties.getMasking().getHmacKeyAlias())) {
            fail("HASH user ID mode requires an external HMAC key alias");
        }
        Set<String> names = new HashSet<>();
        for (var rule : properties.getMasking().getRules()) {
            if (!StringUtils.hasText(rule.getName())
                || !RULE_NAME.matcher(rule.getName()).matches()) {
                fail("masking rule name is invalid");
            }
            if (!names.add(rule.getName())) {
                fail("duplicate masking rule name: " + rule.getName());
            }
            if (rule.getVisiblePrefix() < 0 || rule.getVisibleSuffix() < 0) {
                fail("partial masking prefix/suffix must not be negative");
            }
            if (rule.getMaskingType() == MaskingType.SUBSTITUTION
                && !StringUtils.hasText(rule.getSubstitution())) {
                fail("masking substitution must not be blank");
            }
            validateExpressions(rule);
            if (StringUtils.hasText(rule.getStrategyBean())
                && !beans.containsBean(rule.getStrategyBean())) {
                fail("custom masking strategy bean does not exist: "
                    + rule.getStrategyBean());
            }
            if (secure && rule.getMaskingType() == MaskingType.HASH
                && !StringUtils.hasText(properties.getMasking().getHmacKeyAlias())) {
                fail("HASH masking requires an HMAC key alias in secure environments");
            }
        }
    }

    private static void validateExpressions(
        PlatformLoggingProperties.MaskingRuleProperties rule
    ) {
        var expressions = switch (rule.getMatchType()) {
            case JSON_PATH -> rule.getPaths();
            case REGEX -> rule.getPatterns();
            default -> rule.getFields();
        };
        if (expressions.isEmpty() || expressions.stream().anyMatch(value -> !StringUtils.hasText(value))) {
            fail("masking rule must contain a non-blank expression");
        }
        if (rule.getMatchType() == MaskingMatchType.JSON_PATH) {
            if (expressions.stream().anyMatch(value ->
                !value.matches("\\$(?:\\.[A-Za-z0-9_-]+|\\[(?:\\d+|\\*)])+"))) {
                fail("unsupported JSON path syntax");
            }
        }
        if (rule.getMatchType() == MaskingMatchType.REGEX) {
            for (String expression : expressions) {
                if (expression.length() > 1024 || dangerousRegex(expression)) {
                    fail("masking regex is unsafe");
                }
                try {
                    Pattern.compile(expression);
                } catch (PatternSyntaxException exception) {
                    fail("masking regex is invalid");
                }
            }
        }
    }

    private void validateCrypto() {
        var crypto = properties.getCrypto();
        if (!StringUtils.hasText(crypto.getDefaults().getKeyAlias())) {
            fail("default crypto key alias must not be blank");
        }
        if (crypto.getKeyCache().getTtl() == null
            || crypto.getKeyCache().getTtl().isNegative()
            || crypto.getKeyCache().getTtl().isZero()) {
            fail("crypto key cache TTL must be positive");
        }
        if (isSecureEnvironment()
            && (crypto.isAllowLegacyAlgorithms()
                || properties.getSecurity().isAllowWeakCrypto())) {
            fail("weak or legacy crypto is forbidden in secure environments");
        }
        if (crypto.getProviders().getJasypt().isEnabled()
            && !ClassUtils.isPresent(
                "org.jasypt.encryption.pbe.StandardPBEByteEncryptor",
                getClass().getClassLoader())) {
            fail("Jasypt provider is enabled but Jasypt is not on the classpath");
        }
    }

    private void rejectRawCorePayloadLogger() {
        boolean requestLogging = environment.getProperty(
            "platform.core.web.request-logging-enabled", Boolean.class, false);
        boolean includePayload = environment.getProperty(
            "platform.core.web.include-payload", Boolean.class, false);
        if (requestLogging && includePayload) {
            fail("core raw request payload logging conflicts with secure masking");
        }
    }

    private boolean isSecureEnvironment() {
        Set<String> relaxed = Set.of("local", "dev", "development", "test");
        for (String profile : environment.getActiveProfiles()) {
            if (!relaxed.contains(profile.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        String configured = properties.getEnvironment();
        return configured == null
            || !relaxed.contains(configured.toLowerCase(Locale.ROOT));
    }

    private static boolean dangerousRegex(String expression) {
        return expression.contains(".*.*")
            || expression.matches(".*\\([^)]*[+*][^)]*\\)[+*].*");
    }

    private static void fail(String detail) {
        throw new PlatformLoggingConfigurationException(detail);
    }
}
