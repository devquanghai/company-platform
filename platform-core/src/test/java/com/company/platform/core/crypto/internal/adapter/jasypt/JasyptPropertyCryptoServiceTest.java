package com.company.platform.core.crypto.internal.adapter.jasypt;

import com.company.platform.core.exception.PlatformInfrastructureException;
import org.jasypt.encryption.StringEncryptor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JasyptPropertyCryptoServiceTest {

    private final JasyptPropertyCryptoService service =
        new JasyptPropertyCryptoService(new ReversingStringEncryptor());

    @Test
    void encryptsWrapsAndDecryptsPropertyValues() {
        assertThat(service.encrypt("secret")).isEqualTo("terces");
        assertThat(service.encryptAndWrap("secret")).isEqualTo("ENC(terces)");
        assertThat(service.decrypt("ENC(terces)")).isEqualTo("secret");
        assertThat(service.isEncrypted("ENC(terces)")).isTrue();
    }

    @Test
    void doesNotDoubleEncryptWrappedValues() {
        assertThat(service.encrypt("ENC(terces)")).isEqualTo("terces");
        assertThat(service.encryptAndWrap("ENC(terces)")).isEqualTo("ENC(terces)");
    }

    @Test
    void reportsStableNonSensitiveFailure() {
        var failingService = new JasyptPropertyCryptoService(new FailingStringEncryptor());

        assertThatThrownBy(() -> failingService.encrypt("do-not-expose"))
            .isInstanceOf(PlatformInfrastructureException.class)
            .hasMessage("Property encryption failed")
            .hasMessageNotContaining("do-not-expose")
            .hasNoCause();
    }

    private static final class ReversingStringEncryptor implements StringEncryptor {
        @Override
        public String encrypt(String message) {
            return new StringBuilder(message).reverse().toString();
        }

        @Override
        public String decrypt(String encryptedMessage) {
            return new StringBuilder(encryptedMessage).reverse().toString();
        }
    }

    private static final class FailingStringEncryptor implements StringEncryptor {
        @Override
        public String encrypt(String message) {
            throw new IllegalArgumentException("provider failure");
        }

        @Override
        public String decrypt(String encryptedMessage) {
            throw new IllegalArgumentException("provider failure");
        }
    }
}
