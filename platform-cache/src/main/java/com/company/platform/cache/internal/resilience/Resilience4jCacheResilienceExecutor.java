package com.company.platform.cache.internal.resilience;

import com.company.platform.cache.api.resilience.CacheResilienceExecutor;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.data.redis.RedisConnectionFailureException;

import java.util.Objects;
import java.util.function.Supplier;

public final class Resilience4jCacheResilienceExecutor
    implements CacheResilienceExecutor {

    private final CircuitBreaker circuitBreaker;
    private final Retry retry;
    private final Bulkhead bulkhead;

    public Resilience4jCacheResilienceExecutor(
        CircuitBreaker circuitBreaker,
        Retry retry,
        Bulkhead bulkhead
    ) {
        this.circuitBreaker = Objects.requireNonNull(circuitBreaker, "circuitBreaker");
        this.retry = Objects.requireNonNull(retry, "retry");
        this.bulkhead = Objects.requireNonNull(bulkhead, "bulkhead");
    }

    @Override
    public <T> T executeRead(Supplier<T> invocation) {
        Supplier<T> guardedAttempt = decorateGuarded(invocation);
        return execute(() -> Retry.decorateSupplier(retry, guardedAttempt).get());
    }

    @Override
    public <T> T executeWrite(Supplier<T> invocation) {
        return execute(decorateGuarded(invocation));
    }

    @Override
    public boolean shouldFailOpen(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof CacheResilienceRejectedException rejected) {
                return rejected.getReason()
                    == CacheResilienceRejectedException.Reason.CIRCUIT_OPEN;
            }
            if (current instanceof TransientDataAccessException
                || current instanceof RedisConnectionFailureException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private <T> Supplier<T> decorateGuarded(Supplier<T> invocation) {
        Supplier<T> decorated = CircuitBreaker.decorateSupplier(
            circuitBreaker, invocation);
        return Bulkhead.decorateSupplier(bulkhead, decorated);
    }

    private <T> T execute(Supplier<T> invocation) {
        try {
            return invocation.get();
        } catch (CallNotPermittedException exception) {
            throw new CacheResilienceRejectedException(
                "Cache circuit breaker rejected the operation",
                exception,
                CacheResilienceRejectedException.Reason.CIRCUIT_OPEN);
        } catch (BulkheadFullException exception) {
            throw new CacheResilienceRejectedException(
                "Cache bulkhead rejected the operation",
                exception,
                CacheResilienceRejectedException.Reason.BULKHEAD_FULL);
        }
    }
}
