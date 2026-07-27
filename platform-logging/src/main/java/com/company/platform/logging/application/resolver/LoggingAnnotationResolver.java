package com.company.platform.logging.application.resolver;

import com.company.platform.logging.annotation.logging.Loggable;
import com.company.platform.logging.annotation.logging.NoLogging;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.BridgeMethodResolver;
import org.springframework.core.annotation.AnnotatedElementUtils;

import java.lang.reflect.Method;

public final class LoggingAnnotationResolver {
    public Loggable resolve(Method signatureMethod, Class<?> targetType) {
        Method method = BridgeMethodResolver.findBridgedMethod(
            AopUtils.getMostSpecificMethod(signatureMethod, targetType));
        if (AnnotatedElementUtils.hasAnnotation(method, NoLogging.class)
            || AnnotatedElementUtils.hasAnnotation(targetType, NoLogging.class)) {
            return null;
        }
        Loggable methodValue = AnnotatedElementUtils.findMergedAnnotation(
            method, Loggable.class);
        return methodValue != null ? methodValue
            : AnnotatedElementUtils.findMergedAnnotation(targetType, Loggable.class);
    }

    public Method specific(Method signatureMethod, Class<?> targetType) {
        return BridgeMethodResolver.findBridgedMethod(
            AopUtils.getMostSpecificMethod(signatureMethod, targetType));
    }
}
