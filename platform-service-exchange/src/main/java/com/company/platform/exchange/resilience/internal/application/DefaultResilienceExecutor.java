package com.company.platform.exchange.resilience.internal.application;

import com.company.platform.exchange.autoconfigure.properties.ClientProperties;
import com.company.platform.exchange.client.internal.application.ClientConfigurationResolver;
import com.company.platform.exchange.domain.exception.InvalidClientConfigurationException;
import com.company.platform.exchange.domain.exception.OutboundCircuitOpenException;
import com.company.platform.exchange.domain.exception.OutboundRateLimitException;
import com.company.platform.exchange.domain.exception.OutboundTimeoutException;
import com.company.platform.exchange.api.exception.ServiceExchangeClientException;
import com.company.platform.exchange.resilience.executor.ResilienceExecutionContext;
import com.company.platform.exchange.resilience.executor.ResilienceExecutor;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class DefaultResilienceExecutor implements ResilienceExecutor {
    private static final int MAX_RETRY_ATTEMPTS = 3;

    private final ClientConfigurationResolver resolver;
    private final CircuitBreakerRegistry circuitBreakers;
    private final RetryRegistry retries;
    private final RateLimiterRegistry rateLimiters;
    private final BulkheadRegistry bulkheads;
    private final ConcurrentHashMap<String, Policies> policies = new ConcurrentHashMap<>();

    public DefaultResilienceExecutor(
        ClientConfigurationResolver resolver,
        CircuitBreakerRegistry circuitBreakers,
        RetryRegistry retries,
        RateLimiterRegistry rateLimiters,
        BulkheadRegistry bulkheads
    ) {
        this.resolver = resolver;
        this.circuitBreakers = circuitBreakers;
        this.retries = retries;
        this.rateLimiters = rateLimiters;
        this.bulkheads = bulkheads;
        resolver.clients().forEach((name, client) -> {
            if (client.isEnabled() && client.isResilienceEnabled()) {
                policies.put(name, resolvePolicies(name));
            }
        });
    }

    @Override
    public <T> T execute(ResilienceExecutionContext context, Supplier<T> invocation) {
        ClientProperties client = resolver.resolve(context.getClientName());
        if (!client.isResilienceEnabled()) {
            return invocation.get();
        }
        Policies policy = policies.computeIfAbsent(
            context.getClientName(), this::resolvePolicies);
        Supplier<T> decorated = Retry.decorateSupplier(policy.retry(), invocation);
        decorated = CircuitBreaker.decorateSupplier(policy.circuitBreaker(), decorated);
        decorated = Bulkhead.decorateSupplier(policy.bulkhead(), decorated);
        decorated = RateLimiter.decorateSupplier(policy.rateLimiter(), decorated);
        try {
            return decorated.get();
        } catch (CallNotPermittedException exception) {
            throw new OutboundCircuitOpenException(context.getClientName(), exception);
        } catch (RequestNotPermitted | BulkheadFullException exception) {
            throw new OutboundRateLimitException(context.getClientName(), exception);
        }
    }

    @Override
    public String circuitBreakerState(String clientName) {
        Policies current = policies.get(clientName);
        return current == null ? "NOT_INITIALIZED"
            : current.circuitBreaker().getState().name();
    }

    private Policies resolvePolicies(String clientName) {
        String instance = resolver.resilienceInstance(clientName);
        CircuitBreaker circuitBreaker = circuitBreakers.find(instance)
            .orElseThrow(() -> missing(clientName, "circuitbreaker", instance));
        Retry retry = retries.find(instance)
            .orElseThrow(() -> missing(clientName, "retry", instance));
        RateLimiter rateLimiter = rateLimiters.find(instance)
            .orElseThrow(() -> missing(clientName, "ratelimiter", instance));
        Bulkhead bulkhead = bulkheads.find(instance)
            .orElseThrow(() -> missing(clientName, "bulkhead", instance));
        validate(clientName, retry, circuitBreaker, bulkhead, rateLimiter);
        return new Policies(retry, circuitBreaker, rateLimiter, bulkhead);
    }

    private void validate(
        String clientName,
        Retry retry,
        CircuitBreaker circuitBreaker,
        Bulkhead bulkhead,
        RateLimiter rateLimiter
    ) {
        int attempts = retry.getRetryConfig().getMaxAttempts();
        boolean safePredicate = !retry.getRetryConfig().getExceptionPredicate()
            .test(new IllegalArgumentException("validation"))
            && retry.getRetryConfig().getExceptionPredicate()
                .test(new OutboundTimeoutException(clientName, null));
        if (!safePredicate || attempts < 1 || attempts > MAX_RETRY_ATTEMPTS) {
            throw new InvalidClientConfigurationException(
                clientName,
                "native retry instance must use OutboundRetryPredicate and max-attempts 1..3");
        }
        boolean safeCircuitBreaker = !circuitBreaker.getCircuitBreakerConfig()
            .getRecordExceptionPredicate().test(new IllegalArgumentException("validation"))
            && !circuitBreaker.getCircuitBreakerConfig().getRecordExceptionPredicate()
                .test(new ServiceExchangeClientException(
                    clientName, "GET", 400, false, null))
            && circuitBreaker.getCircuitBreakerConfig().getRecordExceptionPredicate()
                .test(new OutboundTimeoutException(clientName, null))
            && circuitBreaker.getCircuitBreakerConfig().getRecordExceptionPredicate()
                .test(new java.util.concurrent.TimeoutException("validation"));
        if (!safeCircuitBreaker) {
            throw new InvalidClientConfigurationException(
                clientName,
                "native circuit breaker instance must use OutboundCircuitBreakerPredicate");
        }
        if (!bulkhead.getBulkheadConfig().getMaxWaitDuration().isZero()
            || !rateLimiter.getRateLimiterConfig().getTimeoutDuration().isZero()) {
            throw new InvalidClientConfigurationException(
                clientName, "bulkhead and rate limiter must use zero wait for fail-fast admission");
        }
    }

    private InvalidClientConfigurationException missing(
        String client, String kind, String instance
    ) {
        return new InvalidClientConfigurationException(
            client, "missing native resilience4j." + kind + " instance '" + instance + "'");
    }

    private record Policies(
        Retry retry,
        CircuitBreaker circuitBreaker,
        RateLimiter rateLimiter,
        Bulkhead bulkhead
    ) {
    }
}
