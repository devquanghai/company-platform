package com.company.platform.cache.autoconfigure;

import io.github.resilience4j.common.circuitbreaker.configuration.CircuitBreakerConfigCustomizer;
import io.github.resilience4j.common.retry.configuration.RetryConfigCustomizer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.data.redis.RedisConnectionFailureException;

@AutoConfiguration(beforeName = {
    "io.github.resilience4j.springboot.circuitbreaker.autoconfigure.CircuitBreakerAutoConfiguration",
    "io.github.resilience4j.springboot.retry.autoconfigure.RetryAutoConfiguration"
})
@ConditionalOnClass(RetryConfigCustomizer.class)
@ConditionalOnProperty(prefix = "platform.cache", name = "enabled", matchIfMissing = true)
@ConditionalOnProperty(prefix = "platform.cache", name = "provider", havingValue = "redis")
public class CacheResiliencePolicyAutoConfiguration {

    @Bean("platformCacheRetryConfigCustomizer")
    @ConditionalOnMissingBean(name = "platformCacheRetryConfigCustomizer")
    RetryConfigCustomizer platformCacheRetryConfigCustomizer() {
        return RetryConfigCustomizer.of(
            CacheResilienceAutoConfiguration.INSTANCE_NAME,
            builder -> builder.retryOnException(
                exception -> exception instanceof TransientDataAccessException
                    || exception instanceof RedisConnectionFailureException));
    }

    @Bean("platformCacheCircuitBreakerConfigCustomizer")
    @ConditionalOnMissingBean(name = "platformCacheCircuitBreakerConfigCustomizer")
    CircuitBreakerConfigCustomizer platformCacheCircuitBreakerConfigCustomizer() {
        return CircuitBreakerConfigCustomizer.of(
            CacheResilienceAutoConfiguration.INSTANCE_NAME,
            builder -> builder.recordException(
                exception -> exception instanceof DataAccessException));
    }
}
