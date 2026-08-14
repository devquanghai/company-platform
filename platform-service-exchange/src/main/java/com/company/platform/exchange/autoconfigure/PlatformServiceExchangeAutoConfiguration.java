package com.company.platform.exchange.autoconfigure;

import com.company.platform.exchange.client.internal.application.ClientConfigurationResolver;
import com.company.platform.exchange.domain.policy.RetryDecisionPolicy;
import com.company.platform.exchange.resilience.internal.application.DefaultRetryDecisionPolicy;
import com.company.platform.exchange.autoconfigure.properties.ServiceExchangeProperties;
import com.company.platform.exchange.observability.logging.CurlGenerator;
import com.company.platform.exchange.observability.internal.adapter.logging.DefaultCurlGenerator;
import com.company.platform.exchange.observability.internal.adapter.logging.PlatformLoggingOutboundDataMasker;
import com.company.platform.logging.api.masking.DataMaskingService;
import com.company.platform.exchange.observability.logging.OutboundDataMasker;
import com.company.platform.exchange.resilience.internal.adapter.DefaultOutboundFallbackRegistry;
import com.company.platform.exchange.resilience.fallback.OutboundFallbackHandler;
import com.company.platform.exchange.resilience.fallback.OutboundFallbackRegistry;
import com.company.platform.exchange.configuration.internal.ServiceExchangePropertiesValidator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

import java.util.Optional;

@AutoConfiguration
@EnableConfigurationProperties(ServiceExchangeProperties.class)
@ConditionalOnProperty(
    prefix = "platform.service-exchange", name = "enabled",
    havingValue = "true", matchIfMissing = true)
public class PlatformServiceExchangeAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    RetryDecisionPolicy retryDecisionPolicy() {
        return new DefaultRetryDecisionPolicy();
    }

    @Bean
    @ConditionalOnMissingBean
    public ClientConfigurationResolver clientConfigurationResolver(
        ServiceExchangeProperties properties
    ) {
        return new ClientConfigurationResolver(properties);
    }

    @Bean(initMethod = "validate")
    @ConditionalOnMissingBean
    public ServiceExchangePropertiesValidator serviceExchangePropertiesValidator(
        ServiceExchangeProperties properties, ObjectProvider<SslBundles> sslBundles,
        Environment environment
    ) {
        return new ServiceExchangePropertiesValidator(
            properties, Optional.ofNullable(sslBundles.getIfAvailable()), environment);
    }

    @Bean
    @ConditionalOnMissingBean
    public OutboundDataMasker outboundDataMasker(DataMaskingService masking
    ) {
        return new PlatformLoggingOutboundDataMasker(masking);
    }

    @Bean
    @ConditionalOnMissingBean
    public CurlGenerator curlGenerator(OutboundDataMasker masker) {
        return new DefaultCurlGenerator(masker);
    }

    @Bean
    @ConditionalOnMissingBean
    public OutboundFallbackRegistry outboundFallbackRegistry(
        ObjectProvider<OutboundFallbackHandler<?>> handlers
    ) {
        return new DefaultOutboundFallbackRegistry(handlers.orderedStream().toList());
    }

}
