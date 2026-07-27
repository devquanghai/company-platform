package com.company.platform.exchange.resilience.executor;

import com.company.platform.exchange.application.service.ClientConfigurationResolver;
import com.company.platform.exchange.autoconfigure.properties.ClientProperties;
import com.company.platform.exchange.domain.exception.OutboundCircuitOpenException;
import com.company.platform.exchange.domain.exception.OutboundRateLimitException;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class DefaultResilienceExecutor implements ResilienceExecutor {

    private final ClientConfigurationResolver resolver;
    private final ConcurrentHashMap<String, Policies> policies = new ConcurrentHashMap<>();

    public DefaultResilienceExecutor(ClientConfigurationResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    public <T> T execute(ResilienceExecutionContext context, Supplier<T> invocation) {
        ClientProperties client = resolver.resolve(context.getClientName());
        if (!client.getResilience().isEnabled()) {
            return invocation.get();
        }
        Policies policy = policies.computeIfAbsent(
            context.getClientName(), ignored -> create(context.getClientName(), client));
        Supplier<T> decorated = invocation;
        if (client.getResilience().getRetry().isEnabled()) {
            decorated = Retry.decorateSupplier(policy.retry(), decorated);
        }
        if (client.getResilience().getCircuitBreaker().isEnabled()) {
            decorated = CircuitBreaker.decorateSupplier(policy.circuitBreaker(), decorated);
        }
        if (client.getResilience().getBulkhead().isEnabled()) {
            decorated = Bulkhead.decorateSupplier(policy.bulkhead(), decorated);
        }
        if (client.getResilience().getRateLimiter().isEnabled()) {
            decorated = RateLimiter.decorateSupplier(policy.rateLimiter(), decorated);
        }
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
        return current == null ? "NOT_INITIALIZED" : current.circuitBreaker().getState().name();
    }

    private static Policies create(String name, ClientProperties client) {
        var retryProperties = client.getResilience().getRetry();
        Retry retry = Retry.of(name, RetryConfig.custom()
            .maxAttempts(retryProperties.getMaxAttempts())
            .waitDuration(retryProperties.getWaitDuration())
            .retryOnException(DefaultResilienceExecutor::isRetryable)
            .build());

        var circuitProperties = client.getResilience().getCircuitBreaker();
        CircuitBreakerConfig.SlidingWindowType windowType =
            CircuitBreakerConfig.SlidingWindowType.valueOf(
                circuitProperties.getSlidingWindowType());
        CircuitBreaker circuitBreaker = CircuitBreaker.of(name,
            CircuitBreakerConfig.custom()
                .slidingWindowType(windowType)
                .slidingWindowSize(circuitProperties.getSlidingWindowSize())
                .minimumNumberOfCalls(circuitProperties.getMinimumNumberOfCalls())
                .failureRateThreshold(circuitProperties.getFailureRateThreshold())
                .slowCallRateThreshold(circuitProperties.getSlowCallRateThreshold())
                .slowCallDurationThreshold(circuitProperties.getSlowCallDurationThreshold())
                .permittedNumberOfCallsInHalfOpenState(
                    circuitProperties.getPermittedCallsInHalfOpenState())
                .waitDurationInOpenState(circuitProperties.getWaitDurationInOpenState())
                .build());

        var rateProperties = client.getResilience().getRateLimiter();
        RateLimiter rateLimiter = RateLimiter.of(name, RateLimiterConfig.custom()
            .limitForPeriod(rateProperties.getLimitForPeriod())
            .limitRefreshPeriod(rateProperties.getLimitRefreshPeriod())
            .timeoutDuration(rateProperties.getTimeoutDuration())
            .build());

        var bulkheadProperties = client.getResilience().getBulkhead();
        Bulkhead bulkhead = Bulkhead.of(name, BulkheadConfig.custom()
            .maxConcurrentCalls(bulkheadProperties.getMaxConcurrentCalls())
            .maxWaitDuration(bulkheadProperties.getMaxWaitDuration())
            .build());
        return new Policies(retry, circuitBreaker, rateLimiter, bulkhead);
    }

    private static boolean isRetryable(Throwable throwable) {
        return throwable instanceof com.company.platform.exchange.domain.exception.OutboundHttpException http
            && http.isRetryable()
            || throwable instanceof com.company.platform.exchange.domain.exception.OutboundGrpcException grpc
            && grpc.isRetryable();
    }

    private static final class Policies {
        private final Retry retry;
        private final CircuitBreaker circuitBreaker;
        private final RateLimiter rateLimiter;
        private final Bulkhead bulkhead;

        private Policies(
            Retry retry, CircuitBreaker circuitBreaker,
            RateLimiter rateLimiter, Bulkhead bulkhead
        ) {
            this.retry = retry;
            this.circuitBreaker = circuitBreaker;
            this.rateLimiter = rateLimiter;
            this.bulkhead = bulkhead;
        }

        private Retry retry() { return retry; }
        private CircuitBreaker circuitBreaker() { return circuitBreaker; }
        private RateLimiter rateLimiter() { return rateLimiter; }
        private Bulkhead bulkhead() { return bulkhead; }
    }
}
