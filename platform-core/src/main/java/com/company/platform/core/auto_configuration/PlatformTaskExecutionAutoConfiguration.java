package com.company.platform.core.auto_configuration;

import com.company.platform.core.config.task.PlatformAsyncConfigurer;
import com.company.platform.core.config.task.ContextCopyingTaskDecorator;
import com.company.platform.core.config.task.SecurityContextCopyingTaskDecorator;
import com.company.platform.core.configuration.properties.PlatformTaskExecutionProperties;
import com.company.platform.core.auto_configuration.PlatformCoreAutoConfiguration;
import com.company.platform.core.exception.handler.PlatformAsyncExceptionHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskDecorator;
import org.springframework.core.task.support.CompositeTaskDecorator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

@AutoConfiguration(after = PlatformCoreAutoConfiguration.class)
@ConditionalOnProperty(prefix = "platform.core.task-execution", name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties(PlatformTaskExecutionProperties.class)
public class PlatformTaskExecutionAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(ContextCopyingTaskDecorator.class)
    @ConditionalOnProperty(
        prefix = "platform.core.task-execution",
        name = "context-propagation-enabled",
        matchIfMissing = true
    )
    ContextCopyingTaskDecorator platformContextCopyingTaskDecorator() {
        return new ContextCopyingTaskDecorator();
    }

    @Bean(name = {"applicationTaskExecutor", "platformVirtualThreadTaskExecutor"})
    @ConditionalOnMissingBean(AsyncTaskExecutor.class)
    AsyncTaskExecutor platformVirtualThreadTaskExecutor(
        PlatformTaskExecutionProperties properties,
        ObjectProvider<TaskDecorator> decorators
    ) {
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor(properties.getThreadNamePrefix());
        executor.setVirtualThreads(true);
        List<TaskDecorator> availableDecorators = decorators.orderedStream().toList();
        if (!availableDecorators.isEmpty()) {
            executor.setTaskDecorator(new CompositeTaskDecorator(availableDecorators));
        }
        return executor;
    }

    @Bean
    @ConditionalOnMissingBean(AsyncConfigurer.class)
    PlatformAsyncConfigurer platformAsyncConfigurer(
        AsyncTaskExecutor executor,
        PlatformAsyncExceptionHandler exceptionHandler
    ) {
        return new PlatformAsyncConfigurer(executor, exceptionHandler);
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(SecurityContextHolder.class)
    static class SecurityContextPropagationConfiguration {

        @Bean
        @ConditionalOnMissingBean(SecurityContextCopyingTaskDecorator.class)
        @ConditionalOnProperty(
            prefix = "platform.core.task-execution",
            name = "context-propagation-enabled",
            matchIfMissing = true
        )
        SecurityContextCopyingTaskDecorator platformSecurityContextCopyingTaskDecorator() {
            return new SecurityContextCopyingTaskDecorator();
        }
    }
}
