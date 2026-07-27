package com.company.platform.core.config.task;

import org.jspecify.annotations.NonNull;
import org.springframework.core.task.TaskDecorator;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

/** Propagates Spring Security authentication without leaking it to a later task. */
public final class SecurityContextCopyingTaskDecorator implements TaskDecorator {

    @Override
    @NonNull
    public Runnable decorate(@NonNull Runnable runnable) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        SecurityContext captured = SecurityContextHolder.createEmptyContext();
        captured.setAuthentication(authentication);
        return () -> {
            SecurityContext previous = SecurityContextHolder.getContext();
            try {
                SecurityContextHolder.setContext(captured);
                runnable.run();
            } finally {
                SecurityContextHolder.setContext(previous);
            }
        };
    }
}
