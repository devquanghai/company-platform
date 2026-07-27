package com.company.platform.logging.application.service;

import com.company.platform.logging.annotation.crypto.DecryptResult;
import com.company.platform.logging.annotation.crypto.DecryptValue;
import com.company.platform.logging.annotation.crypto.EncryptResult;
import com.company.platform.logging.annotation.crypto.EncryptValue;
import com.company.platform.logging.annotation.logging.AuditLog;
import com.company.platform.logging.annotation.logging.Loggable;
import com.company.platform.logging.annotation.logging.NoLogging;
import com.company.platform.logging.annotation.logging.PerformanceLog;
import com.company.platform.logging.annotation.logging.SecurityLog;
import com.company.platform.logging.annotation.masking.Sensitive;
import com.company.platform.logging.api.logger.PlatformLogger;
import com.company.platform.logging.api.masking.DataMaskingService;
import com.company.platform.logging.application.resolver.LoggingAnnotationResolver;
import com.company.platform.logging.autoconfigure.properties.PlatformLoggingProperties;
import com.company.platform.logging.domain.model.LogCategory;
import com.company.platform.logging.domain.model.LogSeverity;
import com.company.platform.logging.domain.model.SanitizedThrowable;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoggingAspectCoverageTest {

    @Test
    void resolverSupportsMethodTypeComposedAndNoLoggingMetadata() throws Exception {
        LoggingAnnotationResolver resolver = new LoggingAnnotationResolver();

        Method typeMethod = TypeLogged.class.getMethod("execute");
        assertThat(resolver.resolve(typeMethod, TypeLogged.class).event())
            .isEqualTo("TYPE_EVENT");

        Method method = Methods.class.getMethod("success", String.class, String.class);
        assertThat(resolver.resolve(method, Methods.class).event()).isEqualTo("SUCCESS");
        assertThat(resolver.specific(method, Methods.class)).isEqualTo(method);

        assertThat(resolver.resolve(Methods.class.getMethod("audit"), Methods.class))
            .extracting(Loggable::event, Loggable::category)
            .containsExactly("AUDIT_EVENT", LogCategory.AUDIT);
        assertThat(resolver.resolve(Methods.class.getMethod("security"), Methods.class))
            .extracting(Loggable::event, Loggable::category)
            .containsExactly("SECURITY_EVENT", LogCategory.SECURITY);
        assertThat(resolver.resolve(Methods.class.getMethod("performance"), Methods.class))
            .extracting(Loggable::event, Loggable::category)
            .containsExactly("PERFORMANCE_EVENT", LogCategory.PERFORMANCE);
        assertThat(resolver.resolve(Methods.class.getMethod("silent"), Methods.class)).isNull();
        assertThat(resolver.resolve(SilentType.class.getMethod("execute"), SilentType.class))
            .isNull();
        assertThat(resolver.resolve(Methods.class.getMethod("plain"), Methods.class)).isNull();
    }

    @Test
    void aspectLogsSuccessfulArgumentsResultsDurationsAndCryptoExclusions() throws Throwable {
        CapturingLogger logger = new CapturingLogger();
        Masking masking = new Masking();
        PlatformLoggingProperties.MethodLoggingProperties properties =
            new PlatformLoggingProperties.MethodLoggingProperties();
        LoggingAnnotationAspect aspect = new LoggingAnnotationAspect(
            new LoggingAnnotationResolver(), logger, masking, properties);

        Method success = Methods.class.getMethod("success", String.class, String.class);
        ProceedingJoinPoint joinPoint = joinPoint(success,
            new Object[]{"secret", "cipher"}, "raw-result", null);

        assertThat(aspect.log(joinPoint)).isEqualTo("raw-result");
        assertThat(logger.event).isEqualTo("SUCCESS");
        assertThat(logger.message).isEqualTo("Method completed");
        assertThat(logger.category).isEqualTo(LogCategory.AUDIT);
        assertThat(logger.level).isEqualTo(LogSeverity.WARN);
        assertThat(logger.failure).isNull();
        assertThat(logger.fields).containsKeys(
            "method.arguments", "method.result", "duration.ms", "method.category");
        @SuppressWarnings("unchecked")
        Map<String, Object> arguments =
            (Map<String, Object>) logger.fields.get("method.arguments");
        assertThat(arguments.values()).contains("annotated:secret", "<crypto-value-excluded>");
        assertThat(logger.fields.get("method.result")).isEqualTo("sanitized:raw-result");

        Method encryptResult = Methods.class.getMethod("encryptResult", byte[].class);
        ProceedingJoinPoint crypto = joinPoint(
            encryptResult, new Object[]{new byte[]{1}}, "encrypted", null);
        assertThat(aspect.log(crypto)).isEqualTo("encrypted");
        assertThat(logger.fields.get("method.result")).isEqualTo("<crypto-value-excluded>");
        @SuppressWarnings("unchecked")
        Map<String, Object> cryptoArguments =
            (Map<String, Object>) logger.fields.get("method.arguments");
        assertThat(cryptoArguments.values()).containsExactly("<crypto-value-excluded>");
    }

    @Test
    void aspectUsesPropertyDefaultsAndSanitizesSensitiveResult() throws Throwable {
        CapturingLogger logger = new CapturingLogger();
        PlatformLoggingProperties.MethodLoggingProperties properties =
            new PlatformLoggingProperties.MethodLoggingProperties();
        properties.setIncludeArgumentsByDefault(true);
        properties.setIncludeResultByDefault(true);
        LoggingAnnotationAspect aspect = new LoggingAnnotationAspect(
            new LoggingAnnotationResolver(), logger, new Masking(), properties);
        Method method = Methods.class.getMethod("sensitiveResult", String.class);

        assertThat(aspect.log(joinPoint(method, new Object[]{"value"}, "result", null)))
            .isEqualTo("result");
        assertThat(logger.event).isEqualTo("Methods.sensitiveResult");
        assertThat(logger.fields.get("method.result")).isEqualTo("annotated:result");
        assertThat(logger.fields).containsKeys("method.arguments", "method.category")
            .doesNotContainKey("duration.ms");
    }

    @Test
    void aspectLogsAndRethrowsFailuresAccordingToFlags() throws Throwable {
        CapturingLogger logger = new CapturingLogger();
        PlatformLoggingProperties.MethodLoggingProperties properties =
            new PlatformLoggingProperties.MethodLoggingProperties();
        LoggingAnnotationAspect aspect = new LoggingAnnotationAspect(
            new LoggingAnnotationResolver(), logger, new Masking(), properties);
        IllegalStateException failure = new IllegalStateException("password=raw");
        Method method = Methods.class.getMethod("failure");

        assertThatThrownBy(() -> aspect.log(joinPoint(method, new Object[0], null, failure)))
            .isSameAs(failure);
        assertThat(logger.message).isEqualTo("Method failed");
        assertThat(logger.failure).isSameAs(failure);
        assertThat(logger.fields).containsKeys("error", "duration.ms", "method.category");

        properties.setIncludeException(false);
        Method silentFailure = Methods.class.getMethod("failureWithoutException");
        assertThatThrownBy(
            () -> aspect.log(joinPoint(silentFailure, new Object[0], null, failure)))
            .isSameAs(failure);
        assertThat(logger.failure).isNull();
        assertThat(logger.fields).doesNotContainKey("error");
    }

    @Test
    void aspectProceedsWithoutLoggingWhenMetadataIsAbsent() throws Throwable {
        CapturingLogger logger = new CapturingLogger();
        LoggingAnnotationAspect aspect = new LoggingAnnotationAspect(
            new LoggingAnnotationResolver(), logger, new Masking(),
            new PlatformLoggingProperties.MethodLoggingProperties());
        Method plain = Methods.class.getMethod("plain");
        ProceedingJoinPoint joinPoint = joinPoint(plain, new Object[0], "plain-result", null);

        assertThat(aspect.log(joinPoint)).isEqualTo("plain-result");
        assertThat(logger.event).isNull();
    }

    private static ProceedingJoinPoint joinPoint(
        Method method, Object[] arguments, Object result, Throwable failure
    ) throws Throwable {
        MethodSignature signature = (MethodSignature) Proxy.newProxyInstance(
            LoggingAspectCoverageTest.class.getClassLoader(),
            new Class<?>[]{MethodSignature.class},
            (proxy, invoked, values) -> switch (invoked.getName()) {
                case "getMethod" -> method;
                case "getName" -> method.getName();
                case "getDeclaringType" -> method.getDeclaringClass();
                case "getDeclaringTypeName" -> method.getDeclaringClass().getName();
                case "getParameterTypes" -> method.getParameterTypes();
                case "getReturnType" -> method.getReturnType();
                case "getExceptionTypes" -> method.getExceptionTypes();
                case "getParameterNames" -> null;
                case "toShortString", "toLongString", "toString" -> method.toString();
                default -> defaultValue(invoked.getReturnType());
            });
        return (ProceedingJoinPoint) Proxy.newProxyInstance(
            LoggingAspectCoverageTest.class.getClassLoader(),
            new Class<?>[]{ProceedingJoinPoint.class},
            (proxy, invoked, values) -> switch (invoked.getName()) {
                case "getSignature" -> signature;
                case "getTarget", "getThis" -> new Methods();
                case "getArgs" -> arguments;
                case "proceed" -> {
                    if (failure != null) {
                        throw failure;
                    }
                    yield result;
                }
                case "toShortString", "toLongString", "toString" -> method.toString();
                default -> defaultValue(invoked.getReturnType());
            });
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == char.class) {
            return '\0';
        }
        return 0;
    }

    @Loggable(event = "TYPE_EVENT")
    static class TypeLogged {
        public String execute() { return "ok"; }
    }

    @NoLogging
    static class SilentType {
        public String execute() { return "ok"; }
    }

    static class Methods {
        @Loggable(
            event = "SUCCESS", level = LogSeverity.WARN, category = LogCategory.AUDIT,
            includeArguments = true, includeResult = true)
        public String success(
            @Sensitive String secret, @EncryptValue(keyAlias = "key") String cipher
        ) {
            return "result";
        }

        @Loggable(includeArguments = true, includeResult = true)
        @EncryptResult(keyAlias = "key")
        public String encryptResult(@DecryptValue(keyAlias = "key") byte[] value) {
            return "result";
        }

        @Loggable(includeDuration = false)
        @Sensitive
        public String sensitiveResult(String value) { return value; }

        @Loggable
        public String failure() { throw new IllegalStateException(); }

        @Loggable(includeException = false)
        public String failureWithoutException() { throw new IllegalStateException(); }

        @AuditLog(event = "AUDIT_EVENT")
        public void audit() {}

        @SecurityLog(event = "SECURITY_EVENT")
        public void security() {}

        @PerformanceLog(event = "PERFORMANCE_EVENT")
        public void performance() {}

        @NoLogging
        @Loggable
        public void silent() {}

        public String plain() { return "plain"; }
    }

    private static final class CapturingLogger implements PlatformLogger {
        private LogSeverity level;
        private LogCategory category;
        private String event;
        private String message;
        private Map<String, ?> fields = Map.of();
        private Throwable failure;

        @Override
        public void log(
            LogSeverity level, LogCategory category, String eventName,
            String message, Map<String, ?> fields, Throwable throwable
        ) {
            this.level = level;
            this.category = category;
            this.event = eventName;
            this.message = message;
            this.fields = new LinkedHashMap<>(fields);
            this.failure = throwable;
        }

        @Override public void trace(String event, String message, Map<String, ?> fields) {}
        @Override public void debug(String event, String message, Map<String, ?> fields) {}
        @Override public void info(String event, String message, Map<String, ?> fields) {}
        @Override public void warn(String event, String message, Map<String, ?> fields) {}
        @Override public void error(
            String event, String message, Map<String, ?> fields, Throwable throwable) {}
    }

    private static final class Masking implements DataMaskingService {
        @Override public String maskValue(String fieldName, String value) { return "***"; }
        @Override public Object sanitize(Object source) { return "sanitized:" + source; }
        @Override public Object sanitizeAnnotated(Object source, Sensitive annotation) {
            return "annotated:" + source;
        }
        @Override public String sanitizeJson(String json) { return json; }
        @Override public String sanitizeMessage(String message) { return message; }
        @Override public Map<String, Object> sanitizeFields(Map<String, ?> fields) {
            return new LinkedHashMap<>(fields);
        }
        @Override public SanitizedThrowable sanitizeThrowable(Throwable throwable) {
            return SanitizedThrowable.builder()
                .type(throwable.getClass().getName()).message("***").stackTrace(List.of())
                .build();
        }
    }
}
