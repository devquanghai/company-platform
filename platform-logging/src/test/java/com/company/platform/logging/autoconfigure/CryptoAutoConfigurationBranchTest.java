package com.company.platform.logging.autoconfigure;

import com.company.platform.core.context.RequestContextProvider;
import com.company.platform.core.time.TimeProvider;
import com.company.platform.core.trace.CurrentTraceContext;
import com.company.platform.core.trace.TraceContextProvider;
import com.company.platform.logging.api.crypto.CryptoObjectProcessor;
import com.company.platform.logging.api.crypto.CryptoProviderFactory;
import com.company.platform.logging.api.crypto.CryptoRotationService;
import com.company.platform.logging.api.crypto.CryptoService;
import com.company.platform.logging.api.crypto.CryptoStrategyRegistry;
import com.company.platform.logging.api.crypto.KeyProvider;
import com.company.platform.logging.autoconfigure.properties.PlatformLoggingProperties;
import com.company.platform.logging.crypto.envelope.CipherEnvelopeCodec;
import com.company.platform.logging.crypto.key.KeyResolver;
import com.company.platform.logging.domain.exception.PlatformCryptoException;
import com.company.platform.logging.domain.model.CryptoAlgorithm;
import com.company.platform.logging.domain.model.KeyMaterial;
import com.company.platform.logging.domain.model.KeyPurpose;
import com.company.platform.logging.domain.model.KeyReference;
import com.company.platform.logging.domain.model.KeyVersion;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.crypto.spec.SecretKeySpec;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CryptoAutoConfigurationBranchTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(
            PlatformLoggingAutoConfiguration.class, CryptoAutoConfiguration.class))
        .withUserConfiguration(CoreContextConfiguration.class);

    @Test
    void createsCompleteCryptoGraphWithFailClosedDefaultKeyProvider() {
        runner.run(context -> {
            assertThat(context).hasNotFailed()
                .hasSingleBean(KeyResolver.class)
                .hasSingleBean(CipherEnvelopeCodec.class)
                .hasSingleBean(CryptoStrategyRegistry.class)
                .hasSingleBean(CryptoService.class)
                .hasSingleBean(CryptoRotationService.class)
                .hasSingleBean(CryptoObjectProcessor.class);
            assertThat(context.getBeansOfType(CryptoProviderFactory.class)).hasSize(1);
            assertThat(context.getBeansOfType(
                com.company.platform.logging.api.crypto.CryptoStrategy.class)).hasSize(2);
            KeyResolver resolver = context.getBean(KeyResolver.class);
            assertThatThrownBy(() -> resolver.resolveEncryptionKey(reference()))
                .isInstanceOf(PlatformCryptoException.class);
            assertThatThrownBy(() -> resolver.resolveDecryptionKey(reference()))
                .isInstanceOf(PlatformCryptoException.class);
        });
    }

    @Test
    void wrapsCustomProviderWithCacheAndReusesLoadedKey() {
        runner.withUserConfiguration(CustomKeyConfiguration.class).run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(KeyResolver.class);
            CountingKeyProvider provider = context.getBean(CountingKeyProvider.class);
            KeyResolver resolver = context.getBean(KeyResolver.class);
            assertThat(resolver.resolveEncryptionKey(reference()))
                .isSameAs(resolver.resolveEncryptionKey(reference()));
            assertThat(provider.encryptionLoads).hasValue(1);
        });
    }

    @Test
    void cacheDelegatesDecryptionOperations() {
        runner.withUserConfiguration(CustomKeyConfiguration.class)
            .run(context -> {
                assertThat(context).hasNotFailed().hasSingleBean(KeyResolver.class);
                CountingKeyProvider provider = context.getBean(CountingKeyProvider.class);
                KeyResolver resolver = context.getBean(KeyResolver.class);
                resolver.resolveDecryptionKey(reference());
                assertThat(provider.decryptionLoads).hasValue(1);
            });
    }

    private static KeyReference reference() {
        return new KeyReference("application-default", null,
            KeyPurpose.ENCRYPTION, CryptoAlgorithm.AES_GCM_256);
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomKeyConfiguration {
        @Bean CountingKeyProvider countingKeyProvider() {
            return new CountingKeyProvider();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CoreContextConfiguration {
        @Bean TimeProvider timeProvider() {
            return new TimeProvider() {
                @Override public Instant nowInstant() { return Instant.EPOCH; }
                @Override public OffsetDateTime now() { return OffsetDateTime.MIN; }
                @Override public OffsetDateTime now(ZoneId zoneId) {
                    return OffsetDateTime.MIN;
                }
                @Override public ZoneId getDefaultZone() { return ZoneId.of("UTC"); }
            };
        }
        @Bean RequestContextProvider requestContextProvider() {
            return new RequestContextProvider() {
                @Override public String getRequestId() { return "request"; }
                @Override public String getCorrelationId() { return "correlation"; }
            };
        }
        @Bean TraceContextProvider traceContextProvider() {
            return () -> CurrentTraceContext.empty();
        }
    }

    static final class CountingKeyProvider implements KeyProvider {
        private final AtomicInteger encryptionLoads = new AtomicInteger();
        private final AtomicInteger decryptionLoads = new AtomicInteger();
        @Override public KeyMaterial resolveEncryptionKey(KeyReference reference) {
            encryptionLoads.incrementAndGet();
            return material(KeyPurpose.ENCRYPTION);
        }
        @Override public KeyMaterial resolveDecryptionKey(KeyReference reference) {
            decryptionLoads.incrementAndGet();
            return material(KeyPurpose.DECRYPTION);
        }
        private static KeyMaterial material(KeyPurpose purpose) {
            return new KeyMaterial("application-default", new KeyVersion("v1", true),
                purpose, CryptoAlgorithm.AES_GCM_256,
                new SecretKeySpec(new byte[32], "AES"));
        }
    }
}
