package com.company.platform.exchange.autoconfigure.grpc;

import com.company.platform.exchange.adapter.outbound.grpc.DefaultGrpcChannelRegistry;
import com.company.platform.exchange.adapter.outbound.grpc.DefaultGrpcClientFactory;
import com.company.platform.exchange.api.grpc.GrpcCallOperations;
import com.company.platform.exchange.api.grpc.GrpcChannelRegistry;
import com.company.platform.exchange.api.grpc.GrpcClientFactory;
import com.company.platform.exchange.application.service.ClientConfigurationResolver;
import com.company.platform.exchange.application.service.DefaultGrpcCallOperations;
import com.company.platform.exchange.autoconfigure.PlatformServiceExchangeAutoConfiguration;
import com.company.platform.exchange.autoconfigure.resilience.ExchangeResilienceAutoConfiguration;
import com.company.platform.exchange.domain.policy.RetryDecisionPolicy;
import com.company.platform.exchange.resilience.executor.ResilienceExecutor;
import com.company.platform.exchange.resilience.fallback.OutboundFallbackRegistry;
import com.company.platform.exchange.audit.publisher.OutboundCallEventPublisher;
import com.company.platform.exchange.observability.logging.OutboundDataMasker;
import com.company.platform.exchange.observability.metrics.ExchangeMetrics;
import com.company.platform.exchange.autoconfigure.properties.ServiceExchangeProperties;
import com.company.platform.core.context.RequestContextProvider;
import com.company.platform.core.time.TimeProvider;
import com.company.platform.core.trace.TraceContextProvider;
import com.company.platform.exchange.domain.policy.ClientProxyCustomizer;
import com.company.platform.exchange.autoconfigure.audit.ExchangeAuditAutoConfiguration;
import com.company.platform.exchange.autoconfigure.observability.ExchangeObservabilityAutoConfiguration;
import io.grpc.Channel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.context.annotation.Bean;
import org.springframework.grpc.client.GrpcChannelFactory;

import java.util.Optional;

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
        ClientConfigurationResolver resolver, GrpcChannelFactory factory,
        ObjectProvider<SslBundles> sslBundles,
        ClientProxyCustomizer proxyCustomizer,
        ServiceExchangeProperties properties
    ) {
        return new DefaultGrpcChannelRegistry(
            resolver, factory, Optional.ofNullable(sslBundles.getIfAvailable()),
            proxyCustomizer, properties.getShutdownTimeout());
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
        ServiceExchangeProperties properties
    ) {
        return new DefaultGrpcCallOperations(
            resolver, retryPolicy, resilience, fallbacks, events, masker,
            metrics.getIfAvailable(), time, requestContext, traceContext,
            properties.getSourceApplication());
    }
}
