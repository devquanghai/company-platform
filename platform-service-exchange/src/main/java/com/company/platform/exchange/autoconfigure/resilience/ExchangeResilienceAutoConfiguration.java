package com.company.platform.exchange.autoconfigure.resilience;

import com.company.platform.exchange.client.internal.application.ClientConfigurationResolver;
import com.company.platform.exchange.autoconfigure.PlatformServiceExchangeAutoConfiguration;
import com.company.platform.exchange.domain.policy.RetryDecisionPolicy;
import com.company.platform.exchange.resilience.internal.application.DefaultResilienceExecutor;
import com.company.platform.exchange.resilience.internal.application.DefaultRetryDecisionPolicy;
import com.company.platform.exchange.resilience.executor.ResilienceExecutor;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(after = PlatformServiceExchangeAutoConfiguration.class)
@ConditionalOnClass(CircuitBreaker.class)
@ConditionalOnProperty(
    prefix = "platform.service-exchange", name = "enabled",
    havingValue = "true", matchIfMissing = true)
public class ExchangeResilienceAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public RetryDecisionPolicy retryDecisionPolicy(ClientConfigurationResolver resolver) {
        return new DefaultRetryDecisionPolicy(resolver);
    }

    @Bean
    @ConditionalOnMissingBean
    public ResilienceExecutor resilienceExecutor(ClientConfigurationResolver resolver) {
        return new DefaultResilienceExecutor(resolver);
    }
}
