package com.company.platform.core.crypto.rsa;

import com.company.platform.core.exception.PlatformInfrastructureException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RsaServiceImplTest {

    private static KeyPair strongKeyPair;
    private static KeyPair weakKeyPair;

    private final RsaService service = new RsaServiceImpl();

    @BeforeAll
    static void generateKeys() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        strongKeyPair = generator.generateKeyPair();
        generator.initialize(1024);
        weakKeyPair = generator.generateKeyPair();
    }

    @Test
    void encryptAndDecryptRoundTrip() {
        String encrypted = service.encrypt("platform-core", strongKeyPair.getPublic());

        assertThat(encrypted).isNotBlank();
        assertThat(service.decrypt(encrypted, strongKeyPair.getPrivate()))
            .isEqualTo("platform-core");
    }

    @Test
    void hybridEncryptionRoundTrip() {
        String plainText = "hybrid payload ".repeat(100);

        String encrypted = service.encryptMixRsaAes(plainText, strongKeyPair.getPublic());

        assertThat(service.decryptMixRsaAes(encrypted, strongKeyPair.getPrivate()))
            .isEqualTo(plainText);
    }

    @Test
    void signAndVerifyBothValidAndChangedContent() {
        String signature = service.sign("signed-data", strongKeyPair.getPrivate());

        assertThat(service.verify("signed-data", signature, strongKeyPair.getPublic())).isTrue();
        assertThat(service.verify("changed-data", signature, strongKeyPair.getPublic())).isFalse();
    }

    @Test
    void directOperationsRejectWeakKeysAndInvalidInputs() {
        assertInfrastructureFailure(
            () -> service.encrypt("data", weakKeyPair.getPublic()),
            "RSA encryption failed"
        );
        assertInfrastructureFailure(
            () -> service.decrypt("not-base64", strongKeyPair.getPrivate()),
            "RSA decryption failed"
        );
        assertInfrastructureFailure(
            () -> service.sign("data", weakKeyPair.getPrivate()),
            "RSA signing failed"
        );
        assertInfrastructureFailure(
            () -> service.verify("data", "not-base64", strongKeyPair.getPublic()),
            "RSA verify failed"
        );

        PublicKey nonRsaKey = new PublicKey() {
            @Override
            public String getAlgorithm() {
                return "TEST";
            }

            @Override
            public String getFormat() {
                return "RAW";
            }

            @Override
            public byte[] getEncoded() {
                return new byte[]{1};
            }
        };
        assertInfrastructureFailure(
            () -> service.encrypt("data", nonRsaKey),
            "RSA encryption failed"
        );
    }

    @Test
    void hybridDecryptionRejectsMalformedPayloadAndNonRsaPrivateKey() {
        String oneRsaBlock = Base64.getEncoder().encodeToString(new byte[256]);

        assertThatThrownBy(() -> service.decryptMixRsaAes(oneRsaBlock, strongKeyPair.getPrivate()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Invalid hybrid payload");

        PrivateKey nonRsaKey = new PrivateKey() {
            @Override
            public String getAlgorithm() {
                return "TEST";
            }

            @Override
            public String getFormat() {
                return "RAW";
            }

            @Override
            public byte[] getEncoded() {
                return "key".getBytes(StandardCharsets.UTF_8);
            }
        };

        assertThatThrownBy(() -> service.decryptMixRsaAes(
            Base64.getEncoder().encodeToString(new byte[257]),
            nonRsaKey
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Private key must be RSA private key");
    }

    private static void assertInfrastructureFailure(
        Runnable operation,
        String expectedMessage
    ) {
        assertThatThrownBy(operation::run)
            .isInstanceOf(PlatformInfrastructureException.class)
            .hasMessage(expectedMessage)
            .extracting("errorCode")
            .isEqualTo("CORE.CRYPTO.RSA");
    }
}
