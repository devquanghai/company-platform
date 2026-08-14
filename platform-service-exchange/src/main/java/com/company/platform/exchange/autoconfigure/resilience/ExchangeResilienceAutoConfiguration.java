package com.company.platform.exchange.autoconfigure.resilience;

import com.company.platform.exchange.autoconfigure.PlatformServiceExchangeAutoConfiguration;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/** @deprecated Auto-configuration is registered from the resilience feature. */
@Deprecated
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
public class ExchangeResilienceAutoConfiguration extends
    com.company.platform.exchange.resilience.internal.autoconfigure.ExchangeResilienceAutoConfiguration { }
