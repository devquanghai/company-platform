package com.company.platform.core.context.internal.adapter.task;

import com.company.platform.core.exception.internal.adapter.PlatformAsyncExceptionHandler;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.annotation.AsyncConfigurer;

import java.util.Objects;
import java.util.concurrent.Executor;

public final class PlatformAsyncConfigurer implements AsyncConfigurer {

    private final AsyncTaskExecutor executor;
    private final PlatformAsyncExceptionHandler exceptionHandler;

    public PlatformAsyncConfigurer(
        AsyncTaskExecutor executor,
        PlatformAsyncExceptionHandler exceptionHandler
    ) {
        this.executor = Objects.requireNonNull(executor, "executor must not be null");
        this.exceptionHandler = Objects.requireNonNull(
            exceptionHandler,
            "exceptionHandler must not be null"
        );
    }

    @Override
    public Executor getAsyncExecutor() {
        return executor;
    }

    @Override
    public PlatformAsyncExceptionHandler getAsyncUncaughtExceptionHandler() {
        return exceptionHandler;
    }
}
