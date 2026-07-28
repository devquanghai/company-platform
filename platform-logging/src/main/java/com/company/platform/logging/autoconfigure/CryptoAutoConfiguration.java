package com.company.platform.logging.autoconfigure;

import com.company.platform.core.context.RequestContextProvider;
import com.company.platform.core.time.TimeProvider;
import com.company.platform.core.trace.TraceContextProvider;
import com.company.platform.logging.api.crypto.CryptoProviderFactory;
import com.company.platform.logging.api.crypto.CryptoObjectProcessor;
import com.company.platform.logging.api.crypto.CryptoRotationService;
import com.company.platform.logging.api.crypto.CryptoService;
import com.company.platform.logging.api.crypto.CryptoStrategy;
import com.company.platform.logging.api.crypto.CryptoStrategyRegistry;
import com.company.platform.logging.api.crypto.KeyProvider;
import com.company.platform.logging.application.service.DefaultCryptoRotationService;
import com.company.platform.logging.application.service.DefaultCryptoService;
import com.company.platform.logging.audit.publisher.LoggingAuditEventPublisher;
import com.company.platform.logging.autoconfigure.properties.PlatformLoggingProperties;
import com.company.platform.logging.crypto.envelope.CipherEnvelopeCodec;
import com.company.platform.logging.crypto.annotation.DefaultCryptoObjectProcessor;
import com.company.platform.logging.crypto.envelope.VersionedCipherEnvelopeCodec;
import com.company.platform.logging.crypto.factory.DefaultCryptoStrategyRegistry;
import com.company.platform.logging.crypto.factory.JasyptCryptoProviderFactory;
import com.company.platform.logging.crypto.factory.JcaCryptoProviderFactory;
import com.company.platform.logging.crypto.key.KeyResolver;
import com.company.platform.logging.crypto.key.CachingKeyProvider;
import com.company.platform.logging.crypto.key.RejectingKeyProvider;
import com.company.platform.logging.crypto.strategy.AesGcmCryptoStrategy;
import com.company.platform.logging.crypto.strategy.JasyptPbeCryptoStrategy;
import com.company.platform.logging.crypto.strategy.RsaOaepCryptoStrategy;
import com.company.platform.logging.domain.model.CryptoAlgorithm;
import com.company.platform.logging.observability.metrics.LoggingMetrics;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.Map;

@AutoConfiguration(after = {
    PlatformLoggingAutoConfiguration.class,
    LoggingAuditAutoConfiguration.class,
    LoggingMetricsAutoConfiguration.class
})
public class CryptoAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(KeyProvider.class)
    KeyResolver rejectingKeyProvider() { return new RejectingKeyProvider(); }

    @Bean
    @ConditionalOnMissingBean(KeyResolver.class)
    @Primary
    KeyResolver keyResolver(
        KeyProvider provider, PlatformLoggingProperties properties
    ) {
        var cache = properties.getCrypto().getKeyCache();
        KeyProvider delegate = new CachingKeyProvider(
            provider, cache.getTtl(), cache.getMaximumSize());
        return new DelegatingKeyResolver(delegate);
    }

    @Bean
    @ConditionalOnMissingBean
    CipherEnvelopeCodec cipherEnvelopeCodec(PlatformLoggingProperties properties) {
        return new VersionedCipherEnvelopeCodec(
            properties.getCrypto().getMaxEnvelopeLength());
    }

    @Bean("platformAesGcmCryptoStrategy")
    @ConditionalOnMissingBean(name = "platformAesGcmCryptoStrategy")
    CryptoStrategy aesGcmCryptoStrategy() { return new AesGcmCryptoStrategy(); }

    @Bean("platformRsaOaepCryptoStrategy")
    @ConditionalOnMissingBean(name = "platformRsaOaepCryptoStrategy")
    CryptoStrategy rsaOaepCryptoStrategy() { return new RsaOaepCryptoStrategy(); }

    @Bean("platformJasyptPbeCryptoStrategy")
    @ConditionalOnClass(name = "org.jasypt.encryption.pbe.StandardPBEByteEncryptor")
    @ConditionalOnProperty(
        prefix = "platform.logging.crypto.providers.jasypt", name = "enabled",
        havingValue = "true")
    @ConditionalOnMissingBean(name = "platformJasyptPbeCryptoStrategy")
    CryptoStrategy jasyptPbeCryptoStrategy(PlatformLoggingProperties properties) {
        return new JasyptPbeCryptoStrategy(
            properties.getCrypto().getProviders().getJasypt().getAlgorithm());
    }

    @Bean
    @ConditionalOnMissingBean
    CryptoStrategyRegistry cryptoStrategyRegistry(
        Map<String, CryptoStrategy> strategies
    ) {
        return new DefaultCryptoStrategyRegistry(strategies);
    }

    @Bean
    @ConditionalOnMissingBean
    CryptoService cryptoService(
        CryptoStrategyRegistry strategies, KeyResolver keys,
        CipherEnvelopeCodec envelopes,
        ObjectProvider<LoggingAuditEventPublisher> audit,
        ObjectProvider<LoggingMetrics> metrics, TimeProvider time,
        RequestContextProvider requests, TraceContextProvider traces
    ) {
        return new DefaultCryptoService(
            strategies, keys, envelopes, audit.getIfAvailable(),
            metrics.getIfAvailable(), time, requests, traces);
    }

    @Bean
    @ConditionalOnMissingBean
    CryptoRotationService cryptoRotationService(
        CipherEnvelopeCodec envelopes, KeyResolver keys, CryptoService crypto
    ) {
        return new DefaultCryptoRotationService(envelopes, keys, crypto);
    }

    @Bean
    @ConditionalOnMissingBean
    CryptoObjectProcessor cryptoObjectProcessor(CryptoService crypto) {
        return new DefaultCryptoObjectProcessor(crypto);
    }

    @Bean("jcaCryptoProviderFactory")
    @ConditionalOnMissingBean(name = "jcaCryptoProviderFactory")
    CryptoProviderFactory jcaCryptoProviderFactory(
        @Qualifier("platformAesGcmCryptoStrategy") CryptoStrategy aes,
        @Qualifier("platformRsaOaepCryptoStrategy") CryptoStrategy rsa,
        KeyResolver keys, CipherEnvelopeCodec envelopes
    ) {
        return new JcaCryptoProviderFactory(
            Map.of(CryptoAlgorithm.AES_GCM_256, aes,
                CryptoAlgorithm.RSA_OAEP_SHA256, rsa),
            keys, envelopes);
    }

    @Bean("jasyptCryptoProviderFactory")
    @ConditionalOnClass(name = "org.jasypt.encryption.pbe.StandardPBEByteEncryptor")
    @ConditionalOnProperty(
        prefix = "platform.logging.crypto.providers.jasypt", name = "enabled",
        havingValue = "true")
    @ConditionalOnMissingBean(name = "jasyptCryptoProviderFactory")
    CryptoProviderFactory jasyptCryptoProviderFactory(
        @Qualifier("platformJasyptPbeCryptoStrategy") CryptoStrategy strategy,
        KeyResolver keys, CipherEnvelopeCodec envelopes
    ) {
        return new JasyptCryptoProviderFactory(strategy, keys, envelopes);
    }

    private static final class DelegatingKeyResolver
        implements KeyResolver, AutoCloseable {
        private final KeyProvider delegate;
        private DelegatingKeyResolver(KeyProvider delegate) { this.delegate = delegate; }
        @Override public com.company.platform.logging.domain.model.KeyMaterial
        resolveEncryptionKey(com.company.platform.logging.domain.model.KeyReference reference) {
            return delegate.resolveEncryptionKey(reference);
        }
        @Override public com.company.platform.logging.domain.model.KeyMaterial
        resolveDecryptionKey(com.company.platform.logging.domain.model.KeyReference reference) {
            return delegate.resolveDecryptionKey(reference);
        }
        @Override public void close() throws Exception {
            if (delegate instanceof AutoCloseable closeable) {
                closeable.close();
            }
        }
    }
}
