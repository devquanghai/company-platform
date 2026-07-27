package com.company.platform.exchange.autoconfigure;

import com.company.platform.core.json.JsonMapperHelper;
import com.company.platform.exchange.application.service.ClientConfigurationResolver;
import com.company.platform.exchange.autoconfigure.properties.ServiceExchangeProperties;
import com.company.platform.exchange.domain.model.ProxyEndpoint;
import com.company.platform.exchange.domain.policy.ClientProxyCustomizer;
import com.company.platform.exchange.observability.logging.CurlGenerator;
import com.company.platform.exchange.observability.logging.DefaultCurlGenerator;
import com.company.platform.exchange.observability.logging.DefaultOutboundDataMasker;
import com.company.platform.exchange.observability.logging.OutboundDataMasker;
import com.company.platform.exchange.resilience.fallback.DefaultOutboundFallbackRegistry;
import com.company.platform.exchange.resilience.fallback.OutboundFallbackHandler;
import com.company.platform.exchange.resilience.fallback.OutboundFallbackRegistry;
import com.company.platform.exchange.support.ServiceExchangePropertiesValidator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.context.annotation.Bean;

import java.util.HashSet;
import java.util.Optional;

@AutoConfiguration
@EnableConfigurationProperties(ServiceExchangeProperties.class)
@ConditionalOnProperty(
    prefix = "platform.service-exchange", name = "enabled",
    havingValue = "true", matchIfMissing = true)
public class PlatformServiceExchangeAutoConfiguration {

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
        ServiceExchangeProperties properties, ObjectProvider<SslBundles> sslBundles
    ) {
        return new ServiceExchangePropertiesValidator(
            properties, Optional.ofNullable(sslBundles.getIfAvailable()));
    }

    @Bean
    @ConditionalOnMissingBean
    public OutboundDataMasker outboundDataMasker(
        JsonMapperHelper json, ServiceExchangeProperties properties
    ) {
        HashSet<String> headers = new HashSet<>();
        HashSet<String> fields = new HashSet<>();
        properties.getClients().values().forEach(client -> {
            headers.addAll(client.getLogging().getSensitiveHeaders());
            fields.addAll(client.getLogging().getSensitiveFields());
            fields.addAll(client.getLogging().getSensitiveQueryParameters());
        });
        return new DefaultOutboundDataMasker(json, headers, fields);
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

    @Bean
    @ConditionalOnMissingBean
    public ClientProxyCustomizer clientProxyCustomizer() {
        return (context, configured) -> configured;
    }
}
