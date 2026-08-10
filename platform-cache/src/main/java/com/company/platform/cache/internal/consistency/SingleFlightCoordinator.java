package com.company.platform.cache.internal.consistency;

import com.company.platform.cache.domain.exception.PlatformCacheOperationException;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

public final class SingleFlightCoordinator {
    private final ConcurrentMap<String, CompletableFuture<Object>> inFlight =
        new ConcurrentHashMap<>();

    public <T> T execute(
        String identity,
        Duration waitTimeout,
        int maximumInflight,
        Supplier<T> leaderAction
    ) {
        require(identity, waitTimeout, maximumInflight, leaderAction);
        CompletableFuture<Object> candidate = new CompletableFuture<>();
        CompletableFuture<Object> existing = inFlight.putIfAbsent(identity, candidate);
        if (existing == null) {
            if (inFlight.size() > maximumInflight) {
                inFlight.remove(identity, candidate);
                throw failure("CACHE.SINGLEFLIGHT.REJECTED",
                    "Maximum in-flight cache loads exceeded", null);
            }
            return runLeader(identity, candidate, leaderAction);
        }
        return awaitFollower(existing, waitTimeout);
    }

    public int inFlightCount() {
        return inFlight.size();
    }

    @SuppressWarnings("unchecked")
    private <T> T runLeader(
        String identity,
        CompletableFuture<Object> future,
        Supplier<T> action
    ) {
        try {
            T value = action.get();
            future.complete(value);
            return value;
        } catch (Throwable failure) {
            future.completeExceptionally(failure);
            if (failure instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new IllegalStateException("Cache loader failed", failure);
        } finally {
            inFlight.remove(identity, future);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T awaitFollower(
        CompletableFuture<Object> future, Duration timeout
    ) {
        try {
            return (T) future.get(timeout.toNanos(), TimeUnit.NANOSECONDS);
        } catch (TimeoutException exception) {
            throw failure("CACHE.SINGLEFLIGHT.TIMEOUT",
                "Timed out waiting for the active cache loader", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw failure("CACHE.SINGLEFLIGHT.INTERRUPTED",
                "Interrupted while waiting for the active cache loader", exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new IllegalStateException("Cache loader failed", cause);
        }
    }

    private void require(
        String identity,
        Duration timeout,
        int maximumInflight,
        Supplier<?> action
    ) {
        if (identity == null || identity.isBlank()) {
            throw new IllegalArgumentException("identity must not be blank");
        }
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("waitTimeout must be positive");
        }
        if (maximumInflight < 1) {
            throw new IllegalArgumentException("maximumInflight must be positive");
        }
        Objects.requireNonNull(action, "leaderAction");
    }

    private PlatformCacheOperationException failure(
        String code, String message, Throwable cause
    ) {
        return new PlatformCacheOperationException(code, message, cause);
    }
}
