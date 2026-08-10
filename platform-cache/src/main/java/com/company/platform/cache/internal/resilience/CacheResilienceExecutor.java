package com.company.platform.cache.internal.resilience;

import com.company.platform.cache.autoconfigure.properties.ResilienceProperties;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.springframework.data.redis.RedisConnectionFailureException;
import io.github.resilience4j.core.IntervalFunction;

import java.util.Objects;
import java.util.function.Supplier;

public final class CacheResilienceExecutor {
    private final boolean enabled;
    private final boolean retryEnabled;
    private final boolean circuitEnabled;
    private final boolean bulkheadEnabled;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;
    private final Bulkhead bulkhead;
    private final java.util.List<String> retryExceptions;
    private final java.util.List<String> ignoreExceptions;

    public CacheResilienceExecutor(
        String storeName, ResilienceProperties properties
    ) {
        Objects.requireNonNull(storeName, "storeName");
        Objects.requireNonNull(properties, "properties");
        enabled = properties.isEnabled();
        retryEnabled = enabled && properties.getRetry().isEnabled();
        circuitEnabled = enabled && properties.getCircuitBreaker().isEnabled();
        bulkheadEnabled = enabled && properties.getBulkhead().isEnabled();
        retryExceptions = java.util.List.copyOf(
            properties.getRetry().getRetryExceptions());
        ignoreExceptions = java.util.List.copyOf(
            properties.getRetry().getIgnoreExceptions());
        circuitBreaker = CircuitBreaker.of(
            "platform-cache-" + storeName,
            CircuitBreakerConfig.custom()
                .slidingWindowSize(properties.getCircuitBreaker().getSlidingWindowSize())
                .minimumNumberOfCalls(
                    properties.getCircuitBreaker().getMinimumNumberOfCalls())
                .failureRateThreshold(
                    properties.getCircuitBreaker().getFailureRateThreshold())
                .waitDurationInOpenState(
                    properties.getCircuitBreaker().getWaitDurationInOpenState())
                .permittedNumberOfCallsInHalfOpenState(
                    properties.getCircuitBreaker().getPermittedCallsInHalfOpenState())
                .recordException(this::isRetryable)
                .build());
        RetryConfig.Builder<Object> retryBuilder = RetryConfig.custom()
            .maxAttempts(properties.getRetry().getMaxAttempts())
            .retryOnException(this::isRetryable);
        retryBuilder.intervalFunction(properties.getRetry().isExponentialBackoffEnabled()
            ? IntervalFunction.ofExponentialBackoff(
                properties.getRetry().getWaitDuration(),
                properties.getRetry().getMultiplier(),
                properties.getRetry().getMaxWaitDuration())
            : IntervalFunction.of(properties.getRetry().getWaitDuration()));
        retry = Retry.of(
            "platform-cache-" + storeName,
            retryBuilder.build());
        bulkhead = Bulkhead.of(
            "platform-cache-" + storeName,
            BulkheadConfig.custom()
                .maxConcurrentCalls(properties.getBulkhead().getMaxConcurrentCalls())
                .maxWaitDuration(properties.getBulkhead().getMaxWaitDuration())
                .build());
    }

    public <T> T execute(Supplier<T> operation, boolean idempotent) {
        Supplier<T> decorated = Objects.requireNonNull(operation, "operation");
        if (!enabled) {
            return decorated.get();
        }
        if (circuitEnabled) {
            decorated = CircuitBreaker.decorateSupplier(circuitBreaker, decorated);
        }
        if (retryEnabled && idempotent) {
            decorated = Retry.decorateSupplier(retry, decorated);
        }
        if (bulkheadEnabled) {
            decorated = Bulkhead.decorateSupplier(bulkhead, decorated);
        }
        return decorated.get();
    }

    public void execute(Runnable operation, boolean idempotent) {
        execute(() -> {
            operation.run();
            return null;
        }, idempotent);
    }

    public String circuitState() {
        return circuitBreaker.getState().name();
    }

    private boolean isTransient(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof RedisConnectionFailureException
                || current instanceof java.net.ConnectException
                || current instanceof java.net.SocketTimeoutException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private boolean isRetryable(Throwable failure) {
        if (matchesConfigured(failure, ignoreExceptions)) {
            return false;
        }
        if (!retryExceptions.isEmpty()) {
            return matchesConfigured(failure, retryExceptions);
        }
        return isTransient(failure);
    }

    private boolean matchesConfigured(
        Throwable failure, java.util.List<String> configuredTypes
    ) {
        Throwable current = failure;
        while (current != null) {
            Class<?> type = current.getClass();
            while (type != null) {
                if (configuredTypes.contains(type.getName())) {
                    return true;
                }
                type = type.getSuperclass();
            }
            current = current.getCause();
        }
        return false;
    }
}
