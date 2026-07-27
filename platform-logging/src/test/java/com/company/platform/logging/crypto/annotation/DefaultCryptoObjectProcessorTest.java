package com.company.platform.logging.crypto.annotation;

import com.company.platform.logging.annotation.crypto.DecryptValue;
import com.company.platform.logging.annotation.crypto.EncryptValue;
import com.company.platform.logging.api.crypto.CryptoService;
import com.company.platform.logging.domain.exception.PlatformCryptoException;
import com.company.platform.logging.domain.model.CryptoAlgorithm;
import com.company.platform.logging.domain.model.CryptoProviderType;
import com.company.platform.logging.domain.model.CryptoRequest;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultCryptoObjectProcessorTest {

    @Test
    void encryptsAnnotatedInheritedStringAndByteArrayFieldsInPlace() {
        TrackingCryptoService crypto = new TrackingCryptoService();
        DefaultCryptoObjectProcessor processor = new DefaultCryptoObjectProcessor(crypto);
        byte[] originalBytes = "bytes".getBytes(StandardCharsets.UTF_8);
        Encryptable value = new Encryptable("plain", originalBytes);

        assertThat(processor.encryptAnnotatedFields(value)).isSameAs(value);
        assertThat(value.secret).isEqualTo("enc:plain");
        assertThat(value.parentSecret).isSameAs(TrackingCryptoService.ENCRYPTED_BYTES);
        assertThat(value.untouched).isEqualTo("visible");
        assertThat(crypto.requests).hasSize(2);
        CryptoRequest request = crypto.requests.get(0);
        assertThat(request.getProvider()).isEqualTo(CryptoProviderType.JASYPT);
        assertThat(request.getAlgorithm()).isEqualTo(CryptoAlgorithm.PBE);
        assertThat(request.getKeyAlias()).isEqualTo("string-key");
        assertThat(request.getStrategyBean()).isEqualTo("custom");
    }

    @Test
    void decryptsAnnotatedFieldsAndSkipsNullStaticAndWrongDirectionFields() {
        TrackingCryptoService crypto = new TrackingCryptoService();
        DefaultCryptoObjectProcessor processor = new DefaultCryptoObjectProcessor(crypto);
        byte[] cipherBytes = "cipher-bytes".getBytes(StandardCharsets.UTF_8);
        Decryptable value = new Decryptable("cipher", cipherBytes);

        assertThat(processor.decryptAnnotatedFields(value)).isSameAs(value);
        assertThat(value.secret).isEqualTo("dec:cipher");
        assertThat(value.bytes).isSameAs(TrackingCryptoService.DECRYPTED_BYTES);
        assertThat(value.nullSecret).isNull();
        assertThat(value.encryptOnly).isEqualTo("not-processed");
        assertThat(crypto.decryptedInputs).doesNotContain("static-value");
    }

    @Test
    void returnsNullAndLeavesObjectsWithoutRelevantAnnotationsUnchanged() {
        TrackingCryptoService crypto = new TrackingCryptoService();
        DefaultCryptoObjectProcessor processor = new DefaultCryptoObjectProcessor(crypto);
        Plain value = new Plain();

        assertThat(processor.encryptAnnotatedFields((Object) null)).isNull();
        assertThat(processor.decryptAnnotatedFields((Object) null)).isNull();
        assertThat(processor.encryptAnnotatedFields(value)).isSameAs(value);
        assertThat(processor.decryptAnnotatedFields(value)).isSameAs(value);
        assertThat(crypto.requests).isEmpty();
    }

    @Test
    void rejectsFinalAndUnsupportedAnnotatedFields() {
        DefaultCryptoObjectProcessor processor =
            new DefaultCryptoObjectProcessor(new TrackingCryptoService());

        assertThatThrownBy(() -> processor.encryptAnnotatedFields(new FinalField()))
            .isInstanceOf(PlatformCryptoException.class)
            .hasMessageContaining("mutable and accessible");
        assertThatThrownBy(() -> processor.encryptAnnotatedFields(new UnsupportedField()))
            .isInstanceOf(PlatformCryptoException.class)
            .hasMessageContaining("String and byte[]");
        assertThatThrownBy(() -> processor.decryptAnnotatedFields(new UnsupportedDecryptField()))
            .isInstanceOf(PlatformCryptoException.class)
            .hasMessageContaining("String and byte[]");
    }

    private static final class TrackingCryptoService implements CryptoService {
        private static final byte[] ENCRYPTED_BYTES = new byte[] {1};
        private static final byte[] DECRYPTED_BYTES = new byte[] {2};
        private final List<CryptoRequest> requests = new ArrayList<>();
        private final List<String> decryptedInputs = new ArrayList<>();

        @Override public String encrypt(String plaintext, CryptoRequest request) {
            requests.add(request);
            return "enc:" + plaintext;
        }

        @Override public String decrypt(String ciphertext, CryptoRequest request) {
            requests.add(request);
            decryptedInputs.add(ciphertext);
            return "dec:" + ciphertext;
        }

        @Override public byte[] encrypt(byte[] plaintext, CryptoRequest request) {
            requests.add(request);
            return ENCRYPTED_BYTES;
        }

        @Override public byte[] decrypt(byte[] ciphertext, CryptoRequest request) {
            requests.add(request);
            return DECRYPTED_BYTES;
        }
    }

    static class EncryptableParent {
        @EncryptValue(keyAlias = "bytes-key")
        byte[] parentSecret;
        EncryptableParent(byte[] parentSecret) { this.parentSecret = parentSecret; }
    }

    static final class Encryptable extends EncryptableParent {
        @EncryptValue(
            provider = CryptoProviderType.JASYPT, algorithm = CryptoAlgorithm.PBE,
            keyAlias = "string-key", strategyBean = "custom"
        )
        String secret;
        String untouched = "visible";
        Encryptable(String secret, byte[] parentSecret) {
            super(parentSecret);
            this.secret = secret;
        }
    }

    static final class Decryptable {
        static final String STATIC_SECRET = "static-value";
        @DecryptValue(keyAlias = "key") String secret;
        @DecryptValue(keyAlias = "key") byte[] bytes;
        @DecryptValue(keyAlias = "key") String nullSecret;
        @EncryptValue(keyAlias = "key") String encryptOnly = "not-processed";
        Decryptable(String secret, byte[] bytes) {
            this.secret = secret;
            this.bytes = bytes;
        }
    }

    static final class Plain { String value = "plain"; }

    static final class FinalField {
        @EncryptValue(keyAlias = "key") final String value = "plain";
    }

    static final class UnsupportedField {
        @EncryptValue(keyAlias = "key") Integer value = 1;
    }

    static final class UnsupportedDecryptField {
        @DecryptValue(keyAlias = "key") Integer value = 1;
    }
}
