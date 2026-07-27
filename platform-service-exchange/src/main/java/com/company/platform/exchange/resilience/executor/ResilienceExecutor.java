package com.company.platform.exchange.resilience.executor;

import java.util.function.Supplier;

public interface ResilienceExecutor {
    <T> T execute(ResilienceExecutionContext context, Supplier<T> invocation);
    String circuitBreakerState(String clientName);
}
