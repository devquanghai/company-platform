package com.company.platform.exchange.resilience.internal.autoconfigure;

import com.company.platform.exchange.client.internal.application.ClientConfigurationResolver;
import com.company.platform.exchange.autoconfigure.PlatformServiceExchangeAutoConfiguration;
import com.company.platform.exchange.resilience.internal.application.DefaultResilienceExecutor;
import com.company.platform.exchange.resilience.executor.ResilienceExecutor;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import com.company.platform.exchange.resilience.executor.ReactiveResilienceExecutor;
import com.company.platform.exchange.resilience.internal.application.Resilience4jReactiveResilienceExecutor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(
    after = PlatformServiceExchangeAutoConfiguration.class,
    afterName = {
        "io.github.resilience4j.springboot.bulkhead.autoconfigure.BulkheadAutoConfiguration",
        "io.github.resilience4j.springboot.circuitbreaker.autoconfigure.CircuitBreakerAutoConfiguration",
        "io.github.resilience4j.springboot.ratelimiter.autoconfigure.RateLimiterAutoConfiguration",
        "io.github.resilience4j.springboot.retry.autoconfigure.RetryAutoConfiguration",
        "io.github.resilience4j.springboot.timelimiter.autoconfigure.TimeLimiterAutoConfiguration"
    })
@ConditionalOnClass(CircuitBreaker.class)
@ConditionalOnProperty(
    prefix = "platform.service-exchange", name = "enabled",
    havingValue = "true", matchIfMissing = true)
public class ExchangeResilienceAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({
        CircuitBreakerRegistry.class,
        RetryRegistry.class,
        RateLimiterRegistry.class,
        BulkheadRegistry.class
    })
    public ResilienceExecutor resilienceExecutor(
        ClientConfigurationResolver resolver,
        CircuitBreakerRegistry circuitBreakers,
        RetryRegistry retries,
        RateLimiterRegistry rateLimiters,
        BulkheadRegistry bulkheads
    ) {
        return new DefaultResilienceExecutor(
            resolver, circuitBreakers, retries, rateLimiters, bulkheads);
    }

    @Bean
    @ConditionalOnBean({
        CircuitBreakerRegistry.class,
        RetryRegistry.class,
        RateLimiterRegistry.class,
        BulkheadRegistry.class,
        TimeLimiterRegistry.class
    })
    @ConditionalOnMissingBean(ReactiveResilienceExecutor.class)
    ReactiveResilienceExecutor reactiveResilienceExecutor(
        ClientConfigurationResolver resolver,
        CircuitBreakerRegistry circuitBreakers,
        RetryRegistry retries,
        RateLimiterRegistry rateLimiters,
        BulkheadRegistry bulkheads,
        TimeLimiterRegistry timeLimiters
    ) {
        return new Resilience4jReactiveResilienceExecutor(
            resolver, circuitBreakers, retries, rateLimiters, bulkheads, timeLimiters);
    }
}
