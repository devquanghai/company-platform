package com.company.platform.exchange.autoconfigure.http;

import com.company.platform.core.context.RequestContextProvider;
import com.company.platform.core.time.TimeProvider;
import com.company.platform.core.trace.TraceContextProvider;
import com.company.platform.exchange.http.internal.adapter.DefaultHttpClientRegistry;
import com.company.platform.exchange.http.internal.adapter.SecureUriResolver;
import com.company.platform.exchange.api.http.HttpClientRegistry;
import com.company.platform.exchange.api.http.HttpExchangeOperations;
import com.company.platform.exchange.client.internal.application.ClientConfigurationResolver;
import com.company.platform.exchange.http.internal.application.DefaultHttpExchangeOperations;
import com.company.platform.exchange.audit.publisher.OutboundCallEventPublisher;
import com.company.platform.exchange.autoconfigure.PlatformServiceExchangeAutoConfiguration;
import com.company.platform.exchange.autoconfigure.audit.ExchangeAuditAutoConfiguration;
import com.company.platform.exchange.autoconfigure.resilience.ExchangeResilienceAutoConfiguration;
import com.company.platform.exchange.domain.policy.RetryDecisionPolicy;
import com.company.platform.exchange.observability.logging.OutboundDataMasker;
import com.company.platform.exchange.observability.logging.CurlGenerator;
import com.company.platform.exchange.observability.metrics.ExchangeMetrics;
import com.company.platform.exchange.autoconfigure.properties.ServiceExchangeProperties;
import com.company.platform.exchange.autoconfigure.observability.ExchangeObservabilityAutoConfiguration;
import com.company.platform.exchange.domain.policy.ClientProxyCustomizer;
import com.company.platform.exchange.resilience.executor.ResilienceExecutor;
import com.company.platform.exchange.resilience.fallback.OutboundFallbackRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;

import java.util.Optional;

@AutoConfiguration(after = {
    PlatformServiceExchangeAutoConfiguration.class,
    ExchangeResilienceAutoConfiguration.class,
    ExchangeAuditAutoConfiguration.class,
    ExchangeObservabilityAutoConfiguration.class
})
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

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    public HttpClientRegistry httpClientRegistry(
        ClientConfigurationResolver resolver, ObjectProvider<SslBundles> sslBundles
        , ClientProxyCustomizer proxyCustomizer
    ) {
        return new DefaultHttpClientRegistry(
            resolver, Optional.ofNullable(sslBundles.getIfAvailable()), proxyCustomizer);
    }

    @Bean
    @ConditionalOnMissingBean
    public HttpExchangeOperations httpExchangeOperations(
        HttpClientRegistry clients, ClientConfigurationResolver configurations,
        SecureUriResolver uriResolver, RetryDecisionPolicy retryPolicy,
        ResilienceExecutor resilience, OutboundFallbackRegistry fallbacks,
        OutboundCallEventPublisher events, OutboundDataMasker masker,
        TimeProvider time, RequestContextProvider requestContext,
        TraceContextProvider traceContext, CurlGenerator curl,
        ObjectProvider<ExchangeMetrics> metrics,
        ServiceExchangeProperties properties
    ) {
        return new DefaultHttpExchangeOperations(
            clients, configurations, uriResolver, retryPolicy, resilience,
            fallbacks, events, masker, time, requestContext, traceContext,
            curl, metrics.getIfAvailable(), properties.getSourceApplication());
    }
}
