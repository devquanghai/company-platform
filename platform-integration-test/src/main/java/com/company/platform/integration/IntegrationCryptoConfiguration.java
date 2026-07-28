package com.company.platform.integration;

import com.company.platform.logging.api.crypto.KeyProvider;
import com.company.platform.logging.domain.exception.PlatformCryptoException;
import com.company.platform.logging.domain.model.CryptoAlgorithm;
import com.company.platform.logging.domain.model.KeyMaterial;
import com.company.platform.logging.domain.model.KeyReference;
import com.company.platform.logging.domain.model.KeyVersion;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.util.Arrays;

/**
 * Runtime key provider for the integration application only.
 *
 * <p>The key is generated in memory on each application start. Production
 * services must replace this with a KMS, HSM or Vault-backed provider.</p>
 */
@Configuration(proxyBeanMethods = false)
public class IntegrationCryptoConfiguration {

    @Bean
    KeyProvider integrationKeyProvider() {
        return new EphemeralIntegrationKeyProvider(generateKey());
    }

    private static byte[] generateKey() {
        try {
            KeyGenerator generator = KeyGenerator.getInstance("AES");
            generator.init(256);
            SecretKey key = generator.generateKey();
            return key.getEncoded();
        } catch (GeneralSecurityException exception) {
            throw new PlatformCryptoException(
                "PLATFORM.INTEGRATION.CRYPTO_KEY",
                "Unable to initialize the integration encryption key"
            );
        }
    }

    static final class EphemeralIntegrationKeyProvider
        implements KeyProvider, DisposableBean {

        private final byte[] encodedKey;

        EphemeralIntegrationKeyProvider(byte[] encodedKey) {
            this.encodedKey = encodedKey.clone();
        }

        @Override
        public KeyMaterial resolveEncryptionKey(KeyReference reference) {
            return resolve(reference);
        }

        @Override
        public KeyMaterial resolveDecryptionKey(KeyReference reference) {
            return resolve(reference);
        }

        private KeyMaterial resolve(KeyReference reference) {
            if (reference.getAlgorithm() != CryptoAlgorithm.AES_GCM_256) {
                throw new PlatformCryptoException(
                    "PLATFORM.INTEGRATION.CRYPTO_ALGORITHM",
                    "The integration key provider supports AES-GCM-256 only"
                );
            }
            return new KeyMaterial(
                reference.getAlias(),
                new KeyVersion("ephemeral-v1", true),
                reference.getPurpose(),
                reference.getAlgorithm(),
                new SecretKeySpec(encodedKey.clone(), "AES")
            );
        }

        @Override
        public void destroy() {
            Arrays.fill(encodedKey, (byte) 0);
        }
    }
}
