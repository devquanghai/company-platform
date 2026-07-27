package com.company.platform.core.auto_configuration;

import com.company.platform.core.config.task.PlatformAsyncConfigurer;
import com.company.platform.core.config.task.ContextCopyingTaskDecorator;
import com.company.platform.core.config.task.SecurityContextCopyingTaskDecorator;
import com.company.platform.core.configuration.properties.PlatformTaskExecutionProperties;
import com.company.platform.core.auto_configuration.PlatformCoreAutoConfiguration;
import com.company.platform.core.auto_configuration.PlatformTaskExecutionAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class PlatformTaskExecutionAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(
            PlatformCoreAutoConfiguration.class,
            PlatformTaskExecutionAutoConfiguration.class
        ));

    @Test
    void executesApplicationTasksOnNamedVirtualThreads() {
        runner.withPropertyValues("platform.core.task-execution.thread-name-prefix=orders-vt-")
            .run(context -> {
                AsyncTaskExecutor executor = context.getBean(AsyncTaskExecutor.class);
                PlatformAsyncConfigurer configurer = context.getBean(PlatformAsyncConfigurer.class);
                CountDownLatch completed = new CountDownLatch(1);
                AtomicBoolean virtual = new AtomicBoolean();
                AtomicBoolean named = new AtomicBoolean();
                executor.execute(() -> {
                    virtual.set(Thread.currentThread().isVirtual());
                    named.set(Thread.currentThread().getName().startsWith("orders-vt-"));
                    completed.countDown();
                });
                assertThat(completed.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(virtual).isTrue();
                assertThat(named).isTrue();
                assertThat(configurer.getAsyncExecutor()).isSameAs(executor);
                assertThat(configurer.getAsyncUncaughtExceptionHandler()).isNotNull();
                assertThat(context).hasBean("applicationTaskExecutor")
                    .hasBean("platformVirtualThreadTaskExecutor");
            });
    }

    @Test
    void bindsPropertiesCanDisableAndValidatesConfigurer() {
        runner.run(context -> {
            PlatformTaskExecutionProperties properties =
                context.getBean(PlatformTaskExecutionProperties.class);
            properties.setEnabled(false);
            properties.setThreadNamePrefix("custom-");
            properties.setContextPropagationEnabled(false);
            assertThat(properties.isEnabled()).isFalse();
            assertThat(properties.getThreadNamePrefix()).isEqualTo("custom-");
            assertThat(properties.isContextPropagationEnabled()).isFalse();
            PlatformAsyncConfigurer configurer = context.getBean(PlatformAsyncConfigurer.class);
            assertThatNullPointerException().isThrownBy(() -> new PlatformAsyncConfigurer(
                null, configurer.getAsyncUncaughtExceptionHandler()));
            assertThatNullPointerException().isThrownBy(() -> new PlatformAsyncConfigurer(
                context.getBean(AsyncTaskExecutor.class), null));
        });
        runner.withPropertyValues("platform.core.task-execution.enabled=false")
            .run(context -> assertThat(context).doesNotHaveBean(AsyncTaskExecutor.class));
    }

    @Test
    void propagatesRequestMdcAndSecurityContextsAndCanDisablePropagation() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(ContextCopyingTaskDecorator.class)
                .hasSingleBean(SecurityContextCopyingTaskDecorator.class);
            AsyncTaskExecutor executor = context.getBean(AsyncTaskExecutor.class);
            ServletRequestAttributes requestAttributes =
                new ServletRequestAttributes(new MockHttpServletRequest());
            RequestContextHolder.setRequestAttributes(requestAttributes);
            MDC.put("requestId", "request-123");
            SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("ada", "secret")
            );
            CountDownLatch completed = new CountDownLatch(1);
            AtomicReference<Object> request = new AtomicReference<>();
            AtomicReference<String> requestId = new AtomicReference<>();
            AtomicReference<String> principal = new AtomicReference<>();
            try {
                executor.execute(() -> {
                    request.set(RequestContextHolder.getRequestAttributes());
                    requestId.set(MDC.get("requestId"));
                    principal.set(SecurityContextHolder.getContext()
                        .getAuthentication().getName());
                    completed.countDown();
                });
                assertThat(completed.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(request.get()).isSameAs(requestAttributes);
                assertThat(requestId.get()).isEqualTo("request-123");
                assertThat(principal.get()).isEqualTo("ada");
            } finally {
                MDC.clear();
                RequestContextHolder.resetRequestAttributes();
                SecurityContextHolder.clearContext();
                requestAttributes.requestCompleted();
            }
        });

        runner.withPropertyValues(
                "platform.core.task-execution.context-propagation-enabled=false")
            .run(context -> {
                assertThat(context).doesNotHaveBean(ContextCopyingTaskDecorator.class)
                    .doesNotHaveBean(SecurityContextCopyingTaskDecorator.class);
                assertThat(context).hasSingleBean(AsyncTaskExecutor.class);
            });

        ContextCopyingTaskDecorator contextDecorator = new ContextCopyingTaskDecorator();
        SecurityContextCopyingTaskDecorator securityDecorator =
            new SecurityContextCopyingTaskDecorator();
        runner.withBean(ContextCopyingTaskDecorator.class, () -> contextDecorator)
            .withBean(SecurityContextCopyingTaskDecorator.class, () -> securityDecorator)
            .run(context -> {
                assertThat(context.getBean(ContextCopyingTaskDecorator.class))
                    .isSameAs(contextDecorator);
                assertThat(context.getBean(SecurityContextCopyingTaskDecorator.class))
                    .isSameAs(securityDecorator);
            });
    }
}
