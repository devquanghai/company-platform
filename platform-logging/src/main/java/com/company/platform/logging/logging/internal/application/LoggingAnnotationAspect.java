package com.company.platform.logging.logging.internal.application;

import com.company.platform.logging.annotation.crypto.DecryptResult;
import com.company.platform.logging.annotation.crypto.DecryptValue;
import com.company.platform.logging.annotation.crypto.EncryptResult;
import com.company.platform.logging.annotation.crypto.EncryptValue;
import com.company.platform.logging.annotation.logging.Loggable;
import com.company.platform.logging.annotation.masking.Sensitive;
import com.company.platform.logging.api.masking.DataMaskingService;
import com.company.platform.logging.api.logger.PlatformLogger;
import com.company.platform.logging.logging.internal.application.LoggingAnnotationResolver;
import com.company.platform.logging.autoconfigure.properties.PlatformLoggingProperties;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.annotation.AnnotatedElementUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.LinkedHashMap;
import java.util.Map;

@Aspect
@Order(Ordered.HIGHEST_PRECEDENCE + 200)
public final class LoggingAnnotationAspect {
    private static final String CRYPTO_EXCLUDED = "<crypto-value-excluded>";
    private final LoggingAnnotationResolver resolver;
    private final PlatformLogger logger;
    private final DataMaskingService masking;
    private final PlatformLoggingProperties.MethodLoggingProperties properties;

    public LoggingAnnotationAspect(
        LoggingAnnotationResolver resolver, PlatformLogger logger,
        DataMaskingService masking,
        PlatformLoggingProperties.MethodLoggingProperties properties
    ) {
        this.resolver = resolver;
        this.logger = logger;
        this.masking = masking;
        this.properties = properties;
    }

    @Around("""
        !within(com.company.platform.logging..*) && (
            @annotation(com.company.platform.logging.annotation.logging.Loggable)
            || @within(com.company.platform.logging.annotation.logging.Loggable)
            || @annotation(com.company.platform.logging.annotation.logging.AuditLog)
            || @within(com.company.platform.logging.annotation.logging.AuditLog)
            || @annotation(com.company.platform.logging.annotation.logging.SecurityLog)
            || @within(com.company.platform.logging.annotation.logging.SecurityLog)
            || @annotation(com.company.platform.logging.annotation.logging.PerformanceLog)
            || @within(com.company.platform.logging.annotation.logging.PerformanceLog)
        )
        """)
    public Object log(ProceedingJoinPoint joinPoint) throws Throwable {
        Method signature = ((MethodSignature) joinPoint.getSignature()).getMethod();
        Method method = resolver.specific(signature, joinPoint.getTarget().getClass());
        Loggable metadata = resolver.resolve(signature, joinPoint.getTarget().getClass());
        if (metadata == null) {
            return joinPoint.proceed();
        }
        long started = System.nanoTime();
        String event = metadata.event().isBlank()
            ? method.getDeclaringClass().getSimpleName() + "." + method.getName()
            : metadata.event();
        LinkedHashMap<String, Object> fields = new LinkedHashMap<>();
        if (metadata.includeArguments() || properties.isIncludeArgumentsByDefault()) {
            fields.put("method.arguments", arguments(method, joinPoint.getArgs()));
        }
        try {
            Object result = joinPoint.proceed();
            if (metadata.includeResult() || properties.isIncludeResultByDefault()) {
                Sensitive sensitiveResult = AnnotatedElementUtils.findMergedAnnotation(
                    method, Sensitive.class);
                fields.put("method.result", hasCryptoResult(method)
                    ? CRYPTO_EXCLUDED
                    : sensitiveResult == null
                        ? masking.sanitize(result)
                        : masking.sanitizeAnnotated(result, sensitiveResult));
            }
            duration(metadata, fields, started);
            fields.put("method.category", metadata.category().name());
            emit(metadata, event, "Method completed", fields, null);
            return result;
        } catch (Throwable failure) {
            duration(metadata, fields, started);
            if (metadata.includeException() && properties.isIncludeException()) {
                fields.put("error", masking.sanitize(masking.sanitizeThrowable(failure)));
            }
            fields.put("method.category", metadata.category().name());
            emit(metadata, event, "Method failed", fields,
                metadata.includeException() && properties.isIncludeException() ? failure : null);
            throw failure;
        }
    }

    private void emit(
        Loggable metadata, String event, String message,
        Map<String, ?> fields, Throwable failure
    ) {
        logger.log(metadata.level(), metadata.category(), event, message, fields, failure);
    }

    private Map<String, Object> arguments(Method method, Object[] values) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        Parameter[] parameters = method.getParameters();
        for (int index = 0; index < values.length; index++) {
            String name = parameters[index].isNamePresent()
                ? parameters[index].getName() : "arg" + index;
            Sensitive sensitive = AnnotatedElementUtils.findMergedAnnotation(
                parameters[index], Sensitive.class);
            result.put(name, hasCrypto(parameters[index].getAnnotations())
                ? CRYPTO_EXCLUDED
                : sensitive == null
                    ? masking.sanitize(values[index])
                    : masking.sanitizeAnnotated(values[index], sensitive));
        }
        return Map.copyOf(result);
    }

    private static boolean hasCrypto(Annotation[] annotations) {
        for (Annotation annotation : annotations) {
            if (annotation instanceof EncryptValue || annotation instanceof DecryptValue) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasCryptoResult(Method method) {
        return method.isAnnotationPresent(EncryptResult.class)
            || method.isAnnotationPresent(DecryptResult.class);
    }

    private static void duration(
        Loggable metadata, Map<String, Object> fields, long started
    ) {
        if (metadata.includeDuration()) {
            fields.put("duration.ms", (System.nanoTime() - started) / 1_000_000.0d);
        }
    }
}
