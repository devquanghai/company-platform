package com.company.platform.cache.autoconfigure;

import com.company.platform.cache.api.resilience.CacheResilienceExecutor;
import com.company.platform.cache.domain.exception.PlatformCacheConfigurationException;
import com.company.platform.cache.internal.resilience.Resilience4jCacheResilienceExecutor;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.core.functions.Either;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.dao.TransientDataAccessResourceException;

@AutoConfiguration(afterName = {
    "io.github.resilience4j.springboot.bulkhead.autoconfigure.BulkheadAutoConfiguration",
    "io.github.resilience4j.springboot.circuitbreaker.autoconfigure.CircuitBreakerAutoConfiguration",
    "io.github.resilience4j.springboot.retry.autoconfigure.RetryAutoConfiguration"
})
@ConditionalOnClass(CircuitBreakerRegistry.class)
@ConditionalOnProperty(prefix = "platform.cache", name = "enabled", matchIfMissing = true)
@ConditionalOnProperty(prefix = "platform.cache", name = "provider", havingValue = "redis")
public class CacheResilienceAutoConfiguration {
    static final String INSTANCE_NAME = "platformCacheRedis";
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long MAX_RETRY_WAIT_MILLIS = 5_000;

    @Bean
    @ConditionalOnBean({
        CircuitBreakerRegistry.class,
        RetryRegistry.class,
        BulkheadRegistry.class
    })
    @ConditionalOnMissingBean(CacheResilienceExecutor.class)
    CacheResilienceExecutor platformCacheResilienceExecutor(
        CircuitBreakerRegistry circuitBreakers,
        RetryRegistry retries,
        BulkheadRegistry bulkheads
    ) {
        CircuitBreaker circuitBreaker = circuitBreakers.find(INSTANCE_NAME)
            .orElseThrow(() -> missingInstance("circuitbreaker"));
        Retry retry = retries.find(INSTANCE_NAME)
            .orElseThrow(() -> missingInstance("retry"));
        var bulkhead = bulkheads.find(INSTANCE_NAME)
            .orElseThrow(() -> missingInstance("bulkhead"));
        validateConfiguration(circuitBreaker, retry, bulkhead);
        return new Resilience4jCacheResilienceExecutor(
            circuitBreaker,
            retry,
            bulkhead);
    }

    private void validateConfiguration(
        CircuitBreaker circuitBreaker,
        Retry retry,
        io.github.resilience4j.bulkhead.Bulkhead bulkhead
    ) {
        RuntimeException programmingFailure = new IllegalArgumentException(
            "cache resilience policy validation");
        RuntimeException infrastructureFailure =
            new TransientDataAccessResourceException(
                "cache resilience policy validation");

        boolean retrySafe = !retry.getRetryConfig().getExceptionPredicate()
            .test(programmingFailure)
            && retry.getRetryConfig().getExceptionPredicate()
                .test(infrastructureFailure);
        boolean circuitBreakerSafe = !circuitBreaker.getCircuitBreakerConfig()
            .getRecordExceptionPredicate().test(programmingFailure)
            && circuitBreaker.getCircuitBreakerConfig()
                .getRecordExceptionPredicate().test(infrastructureFailure);
        if (!retrySafe || !circuitBreakerSafe) {
            throw new PlatformCacheConfigurationException(
                "Resilience4j instance '" + INSTANCE_NAME
                    + "' must ignore programming failures and handle transient "
                    + "DataAccessException failures");
        }
        int maxAttempts = retry.getRetryConfig().getMaxAttempts();
        long firstRetryWait = retry.getRetryConfig()
            .<Object>getIntervalBiFunction()
            .apply(1, Either.left(infrastructureFailure));
        if (maxAttempts < 1 || maxAttempts > MAX_RETRY_ATTEMPTS
            || firstRetryWait <= 0 || firstRetryWait > MAX_RETRY_WAIT_MILLIS) {
            throw new PlatformCacheConfigurationException(
                "Resilience4j retry instance '" + INSTANCE_NAME
                    + "' requires maxAttempts between 1 and "
                    + MAX_RETRY_ATTEMPTS + " and wait duration between 1ms and "
                    + MAX_RETRY_WAIT_MILLIS + "ms");
        }
        if (!bulkhead.getBulkheadConfig().getMaxWaitDuration().isZero()) {
            throw new PlatformCacheConfigurationException(
                "Resilience4j bulkhead instance '" + INSTANCE_NAME
                    + "' requires maxWaitDuration=0 for fail-fast admission");
        }
    }

    private PlatformCacheConfigurationException missingInstance(String kind) {
        return new PlatformCacheConfigurationException(
            "Missing resilience4j." + kind
                + " instance '" + INSTANCE_NAME + "'");
    }
}
