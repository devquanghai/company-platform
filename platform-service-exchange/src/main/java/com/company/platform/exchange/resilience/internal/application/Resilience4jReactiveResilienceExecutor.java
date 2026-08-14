package com.company.platform.exchange.resilience.internal.application;

import com.company.platform.exchange.autoconfigure.properties.ClientProperties;
import com.company.platform.exchange.client.internal.application.ClientConfigurationResolver;
import com.company.platform.exchange.domain.exception.InvalidClientConfigurationException;
import com.company.platform.exchange.domain.exception.OutboundTimeoutException;
import com.company.platform.exchange.api.exception.ServiceExchangeClientException;
import com.company.platform.exchange.api.client.ServiceExchangeClientType;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.reactor.bulkhead.operator.BulkheadOperator;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.github.resilience4j.reactor.ratelimiter.operator.RateLimiterOperator;
import io.github.resilience4j.reactor.retry.RetryOperator;
import io.github.resilience4j.reactor.timelimiter.TimeLimiterOperator;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import reactor.core.publisher.Mono;
import com.company.platform.exchange.resilience.executor.ReactiveResilienceExecutor;
import com.company.platform.exchange.domain.exception.OutboundCircuitOpenException;
import com.company.platform.exchange.domain.exception.OutboundRateLimitException;

import java.util.concurrent.ConcurrentHashMap;

public final class Resilience4jReactiveResilienceExecutor
    implements ReactiveResilienceExecutor {
    private static final int MAX_RETRY_ATTEMPTS = 3;

    private final ClientConfigurationResolver resolver;
    private final CircuitBreakerRegistry circuitBreakers;
    private final RetryRegistry retries;
    private final RateLimiterRegistry rateLimiters;
    private final BulkheadRegistry bulkheads;
    private final TimeLimiterRegistry timeLimiters;
    private final ConcurrentHashMap<String, Policies> policies = new ConcurrentHashMap<>();

    public Resilience4jReactiveResilienceExecutor(
        ClientConfigurationResolver resolver,
        CircuitBreakerRegistry circuitBreakers,
        RetryRegistry retries,
        RateLimiterRegistry rateLimiters,
        BulkheadRegistry bulkheads,
        TimeLimiterRegistry timeLimiters
    ) {
        this.resolver = resolver;
        this.circuitBreakers = circuitBreakers;
        this.retries = retries;
        this.rateLimiters = rateLimiters;
        this.bulkheads = bulkheads;
        this.timeLimiters = timeLimiters;
        resolver.clients().forEach((name, client) -> {
            if (client.isEnabled() && client.isResilienceEnabled()
                && client.getType() == ServiceExchangeClientType.WEBCLIENT) {
                policies.put(name, resolve(name));
            }
        });
    }

    @Override
    public <T> Mono<T> execute(String clientName, Mono<T> invocation) {
        ClientProperties client = resolver.resolve(clientName);
        if (!client.isResilienceEnabled()) {
            return invocation;
        }
        Policies policy = policies.computeIfAbsent(clientName, this::resolve);
        return invocation
            .transformDeferred(RetryOperator.of(policy.retry()))
            .transformDeferred(TimeLimiterOperator.of(policy.timeLimiter()))
            .transformDeferred(CircuitBreakerOperator.of(policy.circuitBreaker()))
            .transformDeferred(BulkheadOperator.of(policy.bulkhead()))
            .transformDeferred(RateLimiterOperator.of(policy.rateLimiter()))
            .onErrorMap(CallNotPermittedException.class,
                failure -> new OutboundCircuitOpenException(clientName, failure))
            .onErrorMap(failure -> failure instanceof RequestNotPermitted
                    || failure instanceof BulkheadFullException,
                failure -> new OutboundRateLimitException(clientName, failure))
            .onErrorMap(java.util.concurrent.TimeoutException.class,
                failure -> new OutboundTimeoutException(clientName, failure));
    }

    private Policies resolve(String clientName) {
        String instance = resolver.resilienceInstance(clientName);
        Policies resolved = new Policies(
            circuitBreakers.find(instance).orElseThrow(
                () -> missing(clientName, "circuitbreaker", instance)),
            retries.find(instance).orElseThrow(
                () -> missing(clientName, "retry", instance)),
            rateLimiters.find(instance).orElseThrow(
                () -> missing(clientName, "ratelimiter", instance)),
            bulkheads.find(instance).orElseThrow(
                () -> missing(clientName, "bulkhead", instance)),
            timeLimiters.find(instance).orElseThrow(
                () -> missing(clientName, "timelimiter", instance)));
        validate(clientName, resolved);
        return resolved;
    }

    private void validate(String clientName, Policies policies) {
        int attempts = policies.retry().getRetryConfig().getMaxAttempts();
        boolean safeRetry = !policies.retry().getRetryConfig().getExceptionPredicate()
            .test(new IllegalArgumentException("validation"))
            && policies.retry().getRetryConfig().getExceptionPredicate()
                .test(new OutboundTimeoutException(clientName, null));
        if (!safeRetry || attempts < 1 || attempts > MAX_RETRY_ATTEMPTS) {
            throw new InvalidClientConfigurationException(
                clientName,
                "native retry instance must use OutboundRetryPredicate and max-attempts 1..3");
        }
        boolean safeCircuitBreaker = !policies.circuitBreaker()
            .getCircuitBreakerConfig().getRecordExceptionPredicate()
            .test(new IllegalArgumentException("validation"))
            && !policies.circuitBreaker().getCircuitBreakerConfig()
                .getRecordExceptionPredicate().test(new ServiceExchangeClientException(
                    clientName, "GET", 400, false, null))
            && policies.circuitBreaker().getCircuitBreakerConfig()
                .getRecordExceptionPredicate()
                .test(new OutboundTimeoutException(clientName, null))
            && policies.circuitBreaker().getCircuitBreakerConfig()
                .getRecordExceptionPredicate()
                .test(new java.util.concurrent.TimeoutException("validation"));
        if (!safeCircuitBreaker) {
            throw new InvalidClientConfigurationException(
                clientName,
                "native circuit breaker instance must use OutboundCircuitBreakerPredicate");
        }
        if (!policies.bulkhead().getBulkheadConfig().getMaxWaitDuration().isZero()
            || !policies.rateLimiter().getRateLimiterConfig()
                .getTimeoutDuration().isZero()) {
            throw new InvalidClientConfigurationException(
                clientName,
                "bulkhead and rate limiter must use zero wait for fail-fast admission");
        }
    }

    private InvalidClientConfigurationException missing(
        String client, String kind, String instance
    ) {
        return new InvalidClientConfigurationException(
            client, "missing native resilience4j." + kind + " instance '" + instance + "'");
    }

    private record Policies(
        CircuitBreaker circuitBreaker,
        Retry retry,
        RateLimiter rateLimiter,
        Bulkhead bulkhead,
        TimeLimiter timeLimiter
    ) {
    }
}
