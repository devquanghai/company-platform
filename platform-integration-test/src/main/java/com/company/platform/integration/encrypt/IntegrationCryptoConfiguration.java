package com.company.platform.integration.encrypt;

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
import java.security.KeyPair;
import java.security.KeyPairGenerator;
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
        return new EphemeralIntegrationKeyProvider(generateKey(), generateKey());
    }

    @Bean
    KeyPair integrationRsaKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(3072);
            return generator.generateKeyPair();
        } catch (GeneralSecurityException exception) {
            throw new PlatformCryptoException(
                "PLATFORM.INTEGRATION.RSA_KEY",
                "Unable to initialize the integration RSA key pair"
            );
        }
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

        private final byte[] aesKey;
        private final byte[] pbeKey;

        EphemeralIntegrationKeyProvider(byte[] aesKey, byte[] pbeKey) {
            this.aesKey = aesKey.clone();
            this.pbeKey = pbeKey.clone();
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
            if (reference.getAlgorithm() != CryptoAlgorithm.AES_GCM_256
                && reference.getAlgorithm() != CryptoAlgorithm.PBE) {
                throw new PlatformCryptoException(
                    "PLATFORM.INTEGRATION.CRYPTO_ALGORITHM",
                    "The integration key provider supports AES-GCM-256 and PBE only"
                );
            }
            String keyAlgorithm = reference.getAlgorithm() == CryptoAlgorithm.PBE
                ? "PBE" : "AES";
            byte[] key = reference.getAlgorithm() == CryptoAlgorithm.PBE
                ? pbeKey : aesKey;
            return new KeyMaterial(
                reference.getAlias(),
                new KeyVersion("ephemeral-v1", true),
                reference.getPurpose(),
                reference.getAlgorithm(),
                new SecretKeySpec(key.clone(), keyAlgorithm)
            );
        }

        @Override
        public void destroy() {
            Arrays.fill(aesKey, (byte) 0);
            Arrays.fill(pbeKey, (byte) 0);
        }
    }
}
