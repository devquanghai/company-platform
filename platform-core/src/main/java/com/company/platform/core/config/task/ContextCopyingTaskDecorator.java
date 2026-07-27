package com.company.platform.core.config.task;

import org.jspecify.annotations.NonNull;
import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.Map;

/**
 * Propagates request attributes and SLF4J MDC to an asynchronous task and restores
 * the executor thread's prior state after completion.
 */
public final class ContextCopyingTaskDecorator implements TaskDecorator {

    @Override
    @NonNull
    public Runnable decorate(@NonNull Runnable runnable) {
        RequestAttributes capturedRequest = RequestContextHolder.getRequestAttributes();
        Map<String, String> capturedMdc = MDC.getCopyOfContextMap();
        return () -> runWithContext(runnable, capturedRequest, capturedMdc);
    }

    private static void runWithContext(
        Runnable runnable,
        RequestAttributes capturedRequest,
        Map<String, String> capturedMdc
    ) {
        RequestAttributes previousRequest = RequestContextHolder.getRequestAttributes();
        Map<String, String> previousMdc = MDC.getCopyOfContextMap();
        try {
            setRequestAttributes(capturedRequest);
            setMdc(capturedMdc);
            runnable.run();
        } finally {
            setMdc(previousMdc);
            setRequestAttributes(previousRequest);
        }
    }

    private static void setRequestAttributes(RequestAttributes attributes) {
        if (attributes == null) {
            RequestContextHolder.resetRequestAttributes();
        } else {
            RequestContextHolder.setRequestAttributes(attributes);
        }
    }

    private static void setMdc(Map<String, String> context) {
        if (context == null) {
            MDC.clear();
        } else {
            MDC.setContextMap(context);
        }
    }
}
