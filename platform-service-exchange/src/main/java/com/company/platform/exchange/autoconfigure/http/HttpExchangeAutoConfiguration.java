package com.company.platform.exchange.autoconfigure.http;

import com.company.platform.core.context.RequestContextProvider;
import com.company.platform.core.time.TimeProvider;
import com.company.platform.core.trace.TraceContextProvider;
import com.company.platform.exchange.api.http.HttpClientRegistry;
import com.company.platform.exchange.api.http.HttpExchangeOperations;
import com.company.platform.exchange.audit.publisher.OutboundCallEventPublisher;
import com.company.platform.exchange.autoconfigure.PlatformServiceExchangeAutoConfiguration;
import com.company.platform.exchange.autoconfigure.audit.ExchangeAuditAutoConfiguration;
import com.company.platform.exchange.autoconfigure.observability.ExchangeObservabilityAutoConfiguration;
import com.company.platform.exchange.client.internal.application.ClientConfigurationResolver;
import com.company.platform.exchange.client.internal.autoconfigure.ServiceExchangeClientAutoConfiguration;
import com.company.platform.exchange.domain.policy.RetryDecisionPolicy;
import com.company.platform.exchange.http.internal.adapter.SecureUriResolver;
import com.company.platform.exchange.http.internal.application.DefaultHttpExchangeOperations;
import com.company.platform.exchange.observability.logging.CurlGenerator;
import com.company.platform.exchange.observability.logging.OutboundDataMasker;
import com.company.platform.exchange.observability.metrics.ExchangeMetrics;
import com.company.platform.exchange.resilience.executor.ResilienceExecutor;
import com.company.platform.exchange.resilience.fallback.OutboundFallbackRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.web.client.RestClient;

@AutoConfiguration(after = {
    PlatformServiceExchangeAutoConfiguration.class,
    ServiceExchangeClientAutoConfiguration.class,
    ExchangeAuditAutoConfiguration.class,
    ExchangeObservabilityAutoConfiguration.class
}, afterName = "org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration")
@ConditionalOnClass(RestClient.class)
@ConditionalOnProperty(
    prefix = "platform.service-exchange", name = "enabled",
    havingValue = "true", matchIfMissing = true)
public class HttpExchangeAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public SecureUriResolver secureUriResolver() {
        return new SecureUriResolver();
    }

    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnBean(HttpClientRegistry.class)
    @ConditionalOnMissingBean
    public HttpExchangeOperations httpExchangeOperations(
        HttpClientRegistry clients, ClientConfigurationResolver configurations,
        SecureUriResolver uriResolver, RetryDecisionPolicy retryPolicy,
        ResilienceExecutor resilience, OutboundFallbackRegistry fallbacks,
        OutboundCallEventPublisher events, OutboundDataMasker masker,
        TimeProvider time, RequestContextProvider requestContext,
        TraceContextProvider traceContext, CurlGenerator curl,
        ObjectProvider<ExchangeMetrics> metrics,
        Environment environment
    ) {
        return new DefaultHttpExchangeOperations(
            clients, configurations, uriResolver, retryPolicy, resilience,
            fallbacks, events, masker, time, requestContext, traceContext,
            curl, metrics.getIfAvailable(),
            environment.getProperty("spring.application.name", "unknown"));
    }
}
