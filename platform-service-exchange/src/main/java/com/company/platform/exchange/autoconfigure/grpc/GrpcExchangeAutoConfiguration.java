package com.company.platform.exchange.autoconfigure.grpc;

import com.company.platform.exchange.grpc.internal.adapter.DefaultGrpcChannelRegistry;
import com.company.platform.exchange.grpc.internal.adapter.DefaultGrpcClientFactory;
import com.company.platform.exchange.api.grpc.GrpcCallOperations;
import com.company.platform.exchange.api.grpc.GrpcChannelRegistry;
import com.company.platform.exchange.api.grpc.GrpcClientFactory;
import com.company.platform.exchange.client.internal.application.ClientConfigurationResolver;
import com.company.platform.exchange.grpc.internal.application.DefaultGrpcCallOperations;
import com.company.platform.exchange.autoconfigure.PlatformServiceExchangeAutoConfiguration;
import com.company.platform.exchange.autoconfigure.resilience.ExchangeResilienceAutoConfiguration;
import com.company.platform.exchange.domain.policy.RetryDecisionPolicy;
import com.company.platform.exchange.resilience.executor.ResilienceExecutor;
import com.company.platform.exchange.resilience.fallback.OutboundFallbackRegistry;
import com.company.platform.exchange.audit.publisher.OutboundCallEventPublisher;
import com.company.platform.exchange.observability.logging.OutboundDataMasker;
import com.company.platform.exchange.observability.metrics.ExchangeMetrics;
import com.company.platform.core.context.RequestContextProvider;
import com.company.platform.core.time.TimeProvider;
import com.company.platform.core.trace.TraceContextProvider;
import com.company.platform.exchange.autoconfigure.audit.ExchangeAuditAutoConfiguration;
import com.company.platform.exchange.autoconfigure.observability.ExchangeObservabilityAutoConfiguration;
import io.grpc.Channel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.grpc.client.GrpcChannelFactory;

import org.springframework.core.env.Environment;

@AutoConfiguration(after = {
    PlatformServiceExchangeAutoConfiguration.class,
    ExchangeResilienceAutoConfiguration.class,
    ExchangeAuditAutoConfiguration.class,
    ExchangeObservabilityAutoConfiguration.class
})
@ConditionalOnClass({Channel.class, GrpcChannelFactory.class})
@ConditionalOnProperty(
    prefix = "platform.service-exchange", name = "enabled",
    havingValue = "true", matchIfMissing = true)
public class GrpcExchangeAutoConfiguration {

    @Bean
    @ConditionalOnBean(GrpcChannelFactory.class)
    @ConditionalOnMissingBean
    public GrpcChannelRegistry grpcChannelRegistry(
        ClientConfigurationResolver resolver, GrpcChannelFactory factory
    ) {
        return new DefaultGrpcChannelRegistry(resolver, factory);
    }

    @Bean
    @ConditionalOnBean(GrpcChannelRegistry.class)
    @ConditionalOnMissingBean
    public GrpcClientFactory platformGrpcClientFactory(GrpcChannelRegistry channels) {
        return new DefaultGrpcClientFactory(channels);
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnBean(GrpcChannelRegistry.class)
    @ConditionalOnMissingBean
    public GrpcCallOperations grpcCallOperations(
        ClientConfigurationResolver resolver, RetryDecisionPolicy retryPolicy,
        ResilienceExecutor resilience, OutboundFallbackRegistry fallbacks,
        OutboundCallEventPublisher events, OutboundDataMasker masker,
        ObjectProvider<ExchangeMetrics> metrics, TimeProvider time,
        RequestContextProvider requestContext, TraceContextProvider traceContext,
        Environment environment
    ) {
        return new DefaultGrpcCallOperations(
            resolver, retryPolicy, resilience, fallbacks, events, masker,
            metrics.getIfAvailable(), time, requestContext, traceContext,
            environment.getProperty("spring.application.name", "unknown"));
    }
}
