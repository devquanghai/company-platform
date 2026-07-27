package com.company.platform.core.config.task;

import com.company.platform.core.config.task.ContextCopyingTaskDecorator;
import com.company.platform.core.config.task.SecurityContextCopyingTaskDecorator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;

class ContextCopyingTaskDecoratorTest {

    @AfterEach
    void clearContexts() {
        MDC.clear();
        RequestContextHolder.resetRequestAttributes();
        SecurityContextHolder.clearContext();
    }

    @Test
    void propagatesAndRestoresMdcAndRequestAttributes() {
        ServletRequestAttributes captured = attributes("captured");
        RequestContextHolder.setRequestAttributes(captured);
        MDC.put("requestId", "captured-id");
        Runnable decorated = new ContextCopyingTaskDecorator().decorate(() -> {
            assertThat(MDC.get("requestId")).isEqualTo("captured-id");
            assertThat(RequestContextHolder.getRequestAttributes()).isSameAs(captured);
        });

        ServletRequestAttributes previous = attributes("previous");
        RequestContextHolder.setRequestAttributes(previous);
        MDC.put("requestId", "previous-id");
        decorated.run();

        assertThat(MDC.get("requestId")).isEqualTo("previous-id");
        assertThat(RequestContextHolder.getRequestAttributes()).isSameAs(previous);
    }

    @Test
    void propagatesEmptyContextAndRestoresExistingWorkerContext() {
        MDC.clear();
        RequestContextHolder.resetRequestAttributes();
        Runnable decorated = new ContextCopyingTaskDecorator().decorate(() -> {
            assertThat(MDC.getCopyOfContextMap()).isNull();
            assertThat(RequestContextHolder.getRequestAttributes()).isNull();
        });

        ServletRequestAttributes previous = attributes("worker");
        RequestContextHolder.setRequestAttributes(previous);
        MDC.put("worker", "value");
        decorated.run();
        assertThat(MDC.get("worker")).isEqualTo("value");
        assertThat(RequestContextHolder.getRequestAttributes()).isSameAs(previous);
    }

    @Test
    void propagatesAndRestoresSecurityContext() {
        SecurityContextHolder.getContext().setAuthentication(
            new TestingAuthenticationToken("captured-user", "secret")
        );
        Runnable decorated = new SecurityContextCopyingTaskDecorator().decorate(() ->
            assertThat(SecurityContextHolder.getContext().getAuthentication().getName())
                .isEqualTo("captured-user"));

        SecurityContextHolder.getContext().setAuthentication(
            new TestingAuthenticationToken("worker-user", "secret")
        );
        decorated.run();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName())
            .isEqualTo("worker-user");
    }

    private static ServletRequestAttributes attributes(String path) {
        return new ServletRequestAttributes(new MockHttpServletRequest("GET", "/" + path));
    }
}
