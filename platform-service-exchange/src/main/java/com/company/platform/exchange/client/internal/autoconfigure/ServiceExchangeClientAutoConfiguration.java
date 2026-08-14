package com.company.platform.exchange.client.internal.autoconfigure;

import com.company.platform.exchange.api.client.ServiceExchangeClientRegistry;
import com.company.platform.exchange.api.grpc.GrpcCallOperations;
import com.company.platform.exchange.api.http.HttpClientRegistry;
import com.company.platform.exchange.resilience.fallback.OutboundFallbackRegistry;
import com.company.platform.exchange.audit.publisher.OutboundCallEventPublisher;
import com.company.platform.core.time.TimeProvider;
import com.company.platform.core.context.RequestContextProvider;
import com.company.platform.core.trace.TraceContextProvider;
import org.springframework.core.env.Environment;
import com.company.platform.exchange.api.customize.ServiceExchangeClientCustomizer;
import com.company.platform.exchange.autoconfigure.PlatformServiceExchangeAutoConfiguration;
import com.company.platform.exchange.autoconfigure.audit.ExchangeAuditAutoConfiguration;
import com.company.platform.exchange.client.internal.application.ClientConfigurationResolver;
import com.company.platform.exchange.client.internal.adapter.DefaultServiceExchangeClientRegistry;
import com.company.platform.exchange.resilience.executor.ReactiveResilienceExecutor;
import com.company.platform.exchange.resilience.internal.application.UnavailableReactiveResilienceExecutor;
import com.company.platform.exchange.resilience.internal.application.UnavailableResilienceExecutor;
import com.company.platform.exchange.resilience.executor.ResilienceExecutor;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.restclient.autoconfigure.RestClientSsl;
import org.springframework.boot.webclient.autoconfigure.WebClientSsl;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

@AutoConfiguration(
    after = {
        PlatformServiceExchangeAutoConfiguration.class,
        ExchangeAuditAutoConfiguration.class
    },
    afterName = {
        "org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration",
        "org.springframework.boot.webclient.autoconfigure.WebClientAutoConfiguration"
    })
@ConditionalOnProperty(
    prefix = "platform.service-exchange", name = "enabled",
    havingValue = "true", matchIfMissing = true)
public class ServiceExchangeClientAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(ResilienceExecutor.class)
    ResilienceExecutor unavailableResilienceExecutor(
        ClientConfigurationResolver resolver
    ) {
        return new UnavailableResilienceExecutor(resolver);
    }

    @Bean
    @ConditionalOnMissingBean(ReactiveResilienceExecutor.class)
    ReactiveResilienceExecutor unavailableReactiveResilienceExecutor(
        ClientConfigurationResolver resolver
    ) {
        return new UnavailableReactiveResilienceExecutor(resolver);
    }

    @Bean
    @ConditionalOnMissingBean(ServiceExchangeClientRegistry.class)
    DefaultServiceExchangeClientRegistry serviceExchangeClientRegistry(
        ClientConfigurationResolver resolver,
        ObjectProvider<RestClient.Builder> restBuilders,
        ObjectProvider<WebClient.Builder> webBuilders,
        ObjectProvider<RestClientSsl> restSsl,
        ObjectProvider<WebClientSsl> webSsl,
        ObjectProvider<GrpcCallOperations> grpcCalls,
        ObjectProvider<ObservationRegistry> observations,
        ResilienceExecutor blockingResilience,
        ReactiveResilienceExecutor reactiveResilience,
        ObjectProvider<ServiceExchangeClientCustomizer> customizers,
        OutboundFallbackRegistry fallbacks,
        OutboundCallEventPublisher events,
        TimeProvider time,
        RequestContextProvider requestContext,
        TraceContextProvider traceContext,
        Environment environment
    ) {
        return new DefaultServiceExchangeClientRegistry(
            resolver, restBuilders, webBuilders, restSsl, webSsl, grpcCalls, observations,
            blockingResilience, reactiveResilience,
            customizers.orderedStream().toList(), fallbacks, events, time,
            requestContext, traceContext,
            environment.getProperty("spring.application.name", "unknown"));
    }

    @Bean
    @ConditionalOnMissingBean(HttpClientRegistry.class)
    @ConditionalOnBean(DefaultServiceExchangeClientRegistry.class)
    HttpClientRegistry defaultHttpClientRegistry(
        DefaultServiceExchangeClientRegistry registry
    ) {
        return registry;
    }
}
