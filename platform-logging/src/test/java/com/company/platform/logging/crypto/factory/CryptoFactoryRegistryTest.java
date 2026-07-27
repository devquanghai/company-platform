package com.company.platform.logging.crypto.factory;

import com.company.platform.logging.api.crypto.CryptoStrategy;
import com.company.platform.logging.crypto.envelope.CipherEnvelopeCodec;
import com.company.platform.logging.crypto.key.KeyResolver;
import com.company.platform.logging.domain.exception.PlatformCryptoException;
import com.company.platform.logging.domain.model.CryptoAlgorithm;
import com.company.platform.logging.domain.model.CryptoContext;
import com.company.platform.logging.domain.model.CryptoProviderType;
import com.company.platform.logging.domain.model.CipherEnvelope;
import com.company.platform.logging.domain.model.KeyMaterial;
import com.company.platform.logging.domain.model.KeyReference;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CryptoFactoryRegistryTest {

    @Test
    void registryResolvesByProviderAlgorithmAndNameAndKeepsFirstDuplicate() {
        CryptoStrategy first = strategy(CryptoProviderType.JCA, CryptoAlgorithm.AES_GCM_256);
        CryptoStrategy duplicate = strategy(CryptoProviderType.JCA, CryptoAlgorithm.AES_GCM_256);
        LinkedHashMap<String, CryptoStrategy> strategies = new LinkedHashMap<>();
        strategies.put("first", first);
        strategies.put("duplicate", duplicate);
        DefaultCryptoStrategyRegistry registry = new DefaultCryptoStrategyRegistry(strategies);

        assertThat(registry.resolve(CryptoProviderType.JCA, CryptoAlgorithm.AES_GCM_256))
            .isSameAs(first);
        assertThat(registry.find("duplicate")).containsSame(duplicate);
        assertThat(registry.find("missing")).isEmpty();
        assertThatThrownBy(() ->
            registry.resolve(CryptoProviderType.JCA, CryptoAlgorithm.RSA_OAEP_SHA256))
            .isInstanceOf(PlatformCryptoException.class)
            .hasMessageContaining("unavailable");
    }

    @Test
    void jcaFactoryExposesCollaboratorsAndRejectsUnavailableAlgorithms() {
        CryptoStrategy strategy = strategy(
            CryptoProviderType.JCA, CryptoAlgorithm.AES_GCM_256);
        KeyResolver keys = keys();
        CipherEnvelopeCodec envelopes = envelopes();
        JcaCryptoProviderFactory factory = new JcaCryptoProviderFactory(
            Map.of(CryptoAlgorithm.AES_GCM_256, strategy), keys, envelopes);

        assertThat(factory.providerType()).isEqualTo(CryptoProviderType.JCA);
        assertThat(factory.createStrategy(CryptoAlgorithm.AES_GCM_256)).isSameAs(strategy);
        assertThat(factory.createKeyResolver()).isSameAs(keys);
        assertThat(factory.createEnvelopeCodec()).isSameAs(envelopes);
        assertThatThrownBy(() -> factory.createStrategy(CryptoAlgorithm.PBE))
            .isInstanceOf(PlatformCryptoException.class)
            .hasMessageContaining("unavailable");
    }

    @Test
    void jasyptFactoryAcceptsOnlyPbeAndExposesCollaborators() {
        CryptoStrategy strategy = strategy(CryptoProviderType.JASYPT, CryptoAlgorithm.PBE);
        KeyResolver keys = keys();
        CipherEnvelopeCodec envelopes = envelopes();
        JasyptCryptoProviderFactory factory =
            new JasyptCryptoProviderFactory(strategy, keys, envelopes);

        assertThat(factory.providerType()).isEqualTo(CryptoProviderType.JASYPT);
        assertThat(factory.createStrategy(CryptoAlgorithm.PBE)).isSameAs(strategy);
        assertThat(factory.createKeyResolver()).isSameAs(keys);
        assertThat(factory.createEnvelopeCodec()).isSameAs(envelopes);
        assertThatThrownBy(() -> factory.createStrategy(CryptoAlgorithm.AES_GCM_256))
            .isInstanceOf(PlatformCryptoException.class)
            .hasMessageContaining("explicit PBE");
    }

    private static CryptoStrategy strategy(
        CryptoProviderType provider, CryptoAlgorithm algorithm
    ) {
        return new CryptoStrategy() {
            @Override public CryptoProviderType provider() {
                return provider;
            }

            @Override public CryptoAlgorithm algorithm() {
                return algorithm;
            }

            @Override public byte[] encrypt(byte[] plaintext, CryptoContext context) {
                return plaintext;
            }

            @Override public byte[] decrypt(byte[] ciphertext, CryptoContext context) {
                return ciphertext;
            }
        };
    }

    private static KeyResolver keys() {
        return new KeyResolver() {
            @Override public KeyMaterial resolveEncryptionKey(KeyReference reference) {
                return null;
            }

            @Override public KeyMaterial resolveDecryptionKey(KeyReference reference) {
                return null;
            }
        };
    }

    private static CipherEnvelopeCodec envelopes() {
        return new CipherEnvelopeCodec() {
            @Override public String encode(CipherEnvelope envelope) {
                return "";
            }

            @Override public CipherEnvelope decode(String encoded) {
                return null;
            }
        };
    }
}
