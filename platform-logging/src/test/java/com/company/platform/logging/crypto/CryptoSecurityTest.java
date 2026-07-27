package com.company.platform.logging.crypto;

import com.company.platform.logging.api.crypto.KeyProvider;
import com.company.platform.logging.application.service.DefaultCryptoRotationService;
import com.company.platform.logging.application.service.DefaultCryptoService;
import com.company.platform.logging.crypto.envelope.VersionedCipherEnvelopeCodec;
import com.company.platform.logging.crypto.factory.DefaultCryptoStrategyRegistry;
import com.company.platform.logging.crypto.key.CachingKeyProvider;
import com.company.platform.logging.crypto.strategy.AesGcmCryptoStrategy;
import com.company.platform.logging.crypto.strategy.RsaOaepCryptoStrategy;
import com.company.platform.logging.domain.exception.PlatformCryptoException;
import com.company.platform.logging.domain.model.CipherEnvelope;
import com.company.platform.logging.domain.model.CryptoAlgorithm;
import com.company.platform.logging.domain.model.CryptoContext;
import com.company.platform.logging.domain.model.CryptoProviderType;
import com.company.platform.logging.domain.model.CryptoRequest;
import com.company.platform.logging.domain.model.KeyMaterial;
import com.company.platform.logging.domain.model.KeyPurpose;
import com.company.platform.logging.domain.model.KeyReference;
import com.company.platform.logging.domain.model.KeyVersion;
import org.junit.jupiter.api.Test;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CryptoSecurityTest {

    @Test
    void envelopeRoundTripsDefensivelyAndRejectsMalformedOrTamperedMetadata() {
        var codec = new VersionedCipherEnvelopeCodec(4096);
        byte[] nonce = new byte[] {1, 2, 3};
        byte[] ciphertext = new byte[] {4, 5, 6};
        CipherEnvelope envelope = CipherEnvelope.builder().formatVersion("v1")
            .provider(CryptoProviderType.JCA).algorithm(CryptoAlgorithm.AES_GCM_256)
            .keyAlias("application-data").keyVersion("v7").mode("DIRECT")
            .nonce(nonce).ciphertext(ciphertext).authenticationTag(new byte[] {7}).build();
        nonce[0] = 99;
        ciphertext[0] = 99;

        String encoded = codec.encode(envelope);
        CipherEnvelope decoded = codec.decode(encoded);
        assertThat(decoded.getNonce()).containsExactly(1, 2, 3);
        assertThat(decoded.getCiphertext()).containsExactly(4, 5, 6);
        byte[] copy = decoded.getCiphertext();
        copy[0] = 0;
        assertThat(decoded.getCiphertext()).containsExactly(4, 5, 6);
        assertThat(decoded.canonicalHeader()).contains("application-data", "v7", "DIRECT");
        assertThat(decoded.toString()).doesNotContain("application-data", "v7");

        assertThatThrownBy(() -> codec.decode(null)).isInstanceOf(PlatformCryptoException.class);
        assertThatThrownBy(() -> codec.decode("ENC[v2:JCA:AES_GCM_256:a:b:DIRECT:-:-:YQ:-]"))
            .isInstanceOf(PlatformCryptoException.class);
        assertThatThrownBy(() -> codec.decode(encoded.replace(":DIRECT:", ":UNKNOWN:")))
            .isInstanceOf(PlatformCryptoException.class);
        assertThatThrownBy(() -> codec.decode(encoded + "x".repeat(4096)))
            .isInstanceOf(PlatformCryptoException.class);
        assertThatThrownBy(() -> codec.encode(CipherEnvelope.builder().formatVersion("v1")
            .provider(CryptoProviderType.JCA).algorithm(CryptoAlgorithm.AES_GCM_256)
            .keyAlias("bad alias").keyVersion("v1").mode("DIRECT")
            .ciphertext(new byte[] {1}).build())).isInstanceOf(PlatformCryptoException.class);
    }

    @Test
    void aesGcmAuthenticatesCiphertextAadAndEnvelopeMetadata() {
        var strategy = new AesGcmCryptoStrategy();
        var request = request(CryptoAlgorithm.AES_GCM_256, "aes-key", null,
            "tenant-a".getBytes(StandardCharsets.UTF_8));
        KeyMaterial encryption = aesMaterial("aes-key", "v1", KeyPurpose.ENCRYPTION,
            (byte) 3);
        var encryptContext = new CryptoContext(request, encryption, null);
        byte[] plaintext = "highly-sensitive".getBytes(StandardCharsets.UTF_8);
        var result = strategy.encryptResult(plaintext, encryptContext);
        CipherEnvelope envelope = envelope(request, encryption, result);
        KeyMaterial decryption = aesMaterial("aes-key", "v1", KeyPurpose.DECRYPTION,
            (byte) 3);
        var decryptContext = new CryptoContext(request, decryption, envelope);

        assertThat(strategy.decrypt(result.getCiphertext(), decryptContext))
            .isEqualTo(plaintext);
        assertThat(result.getNonce()).hasSize(12);
        assertThat(result.getAuthenticationTag()).hasSize(16);
        assertThat(strategy.provider()).isEqualTo(CryptoProviderType.JCA);
        assertThat(strategy.algorithm()).isEqualTo(CryptoAlgorithm.AES_GCM_256);

        CryptoRequest wrongAad = request(CryptoAlgorithm.AES_GCM_256, "aes-key", null,
            "tenant-b".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> strategy.decrypt(result.getCiphertext(),
            new CryptoContext(wrongAad, decryption, envelope)))
            .isInstanceOf(PlatformCryptoException.class);
        byte[] tamperedTag = envelope.getAuthenticationTag();
        tamperedTag[0] ^= 1;
        CipherEnvelope tampered = copyEnvelope(envelope, envelope.getCiphertext(), tamperedTag);
        assertThatThrownBy(() -> strategy.decrypt(tampered.getCiphertext(),
            new CryptoContext(request, decryption, tampered)))
            .isInstanceOf(PlatformCryptoException.class);
        assertThatThrownBy(() -> strategy.decrypt(new byte[10],
            new CryptoContext(request, decryption, null)))
            .isInstanceOf(PlatformCryptoException.class);
        assertThatThrownBy(() -> strategy.encryptResult(plaintext,
            new CryptoContext(request,
                new KeyMaterial("aes-key", new KeyVersion("v1", true),
                    KeyPurpose.ENCRYPTION, CryptoAlgorithm.AES_GCM_256,
                    new SecretKeySpec(new byte[16], "AES")), null)))
            .isInstanceOf(PlatformCryptoException.class);
    }

    @Test
    void rsaOaepSupportsDirectAndHybridAndRejectsWrongKeysOrMetadata() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        var strategy = new RsaOaepCryptoStrategy();
        var request = request(CryptoAlgorithm.RSA_OAEP_SHA256, "rsa-key", null,
            "tenant-a".getBytes(StandardCharsets.UTF_8));

        byte[] shortValue = "small".getBytes(StandardCharsets.UTF_8);
        KeyMaterial publicMaterial = rsaMaterial(pair.getPublic(), KeyPurpose.ENCRYPTION);
        var direct = strategy.encryptResult(shortValue,
            new CryptoContext(request, publicMaterial, null));
        CipherEnvelope directEnvelope = envelope(request, publicMaterial, direct);
        KeyMaterial privateMaterial = rsaMaterial(pair.getPrivate(), KeyPurpose.DECRYPTION);
        assertThat(direct.getMode()).isEqualTo("DIRECT");
        assertThat(strategy.decrypt(direct.getCiphertext(),
            new CryptoContext(request, privateMaterial, directEnvelope))).isEqualTo(shortValue);

        byte[] largeValue = "x".repeat(2048).getBytes(StandardCharsets.UTF_8);
        var hybrid = strategy.encryptResult(largeValue,
            new CryptoContext(request, publicMaterial, null));
        CipherEnvelope hybridEnvelope = envelope(request, publicMaterial, hybrid);
        assertThat(hybrid.getMode()).isEqualTo("HYBRID");
        assertThat(hybrid.getWrappedKey()).isNotEmpty();
        assertThat(strategy.decrypt(hybrid.getCiphertext(),
            new CryptoContext(request, privateMaterial, hybridEnvelope))).isEqualTo(largeValue);

        CryptoRequest wrongAad = request(CryptoAlgorithm.RSA_OAEP_SHA256, "rsa-key", null,
            "tenant-b".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> strategy.decrypt(direct.getCiphertext(),
            new CryptoContext(wrongAad, privateMaterial, directEnvelope)))
            .isInstanceOf(PlatformCryptoException.class);
        assertThatThrownBy(() -> strategy.encryptResult(shortValue,
            new CryptoContext(request, privateMaterial, null)))
            .isInstanceOf(PlatformCryptoException.class);
        assertThatThrownBy(() -> strategy.decrypt(direct.getCiphertext(),
            new CryptoContext(request, publicMaterial, directEnvelope)))
            .isInstanceOf(PlatformCryptoException.class);

        generator.initialize(1024);
        KeyPair weak = generator.generateKeyPair();
        assertThatThrownBy(() -> strategy.encryptResult(shortValue,
            new CryptoContext(request,
                new KeyMaterial("rsa-key", new KeyVersion("v1", true),
                    KeyPurpose.ENCRYPTION, CryptoAlgorithm.RSA_OAEP_SHA256,
                    weak.getPublic()), null))).isInstanceOf(PlatformCryptoException.class);
    }

    @Test
    void serviceRoundTripsVersionsValidatesPolicyAndRotates() {
        var codec = new VersionedCipherEnvelopeCodec(1_048_576);
        var strategy = new AesGcmCryptoStrategy();
        var registry = new DefaultCryptoStrategyRegistry(Map.of("aes", strategy));
        MutableAesKeyProvider keys = new MutableAesKeyProvider();
        var service = new DefaultCryptoService(registry, keys, codec);
        CryptoRequest request = request(CryptoAlgorithm.AES_GCM_256, "aes-key", null,
            new byte[0]);

        String encryptedV1 = service.encrypt("secret-value", request);
        assertThat(encryptedV1).startsWith("ENC[").doesNotContain("secret-value");
        assertThat(service.decrypt(encryptedV1, request)).isEqualTo("secret-value");
        assertThat(service.decrypt(service.encrypt(
            "unicode-✓".getBytes(StandardCharsets.UTF_8), request), request))
            .isEqualTo("unicode-✓".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> service.encrypt((String) null, request))
            .isInstanceOf(PlatformCryptoException.class);
        assertThatThrownBy(() -> service.decrypt((String) null, request))
            .isInstanceOf(PlatformCryptoException.class);
        assertThatThrownBy(() -> service.encrypt(new byte[] {1}, null))
            .isInstanceOf(PlatformCryptoException.class);
        assertThatThrownBy(() -> service.decrypt("not-an-envelope", request))
            .isInstanceOf(PlatformCryptoException.class);
        assertThatThrownBy(() -> service.decrypt(encryptedV1,
            request(CryptoAlgorithm.AES_GCM_256, "wrong-alias", null, new byte[0])))
            .isInstanceOf(PlatformCryptoException.class);

        var rotation = new DefaultCryptoRotationService(codec, keys, service);
        assertThat(rotation.requiresRotation(encryptedV1)).isFalse();
        keys.currentVersion = "v2";
        assertThat(rotation.requiresRotation(encryptedV1)).isTrue();
        String encryptedV2 = rotation.rotate(encryptedV1);
        assertThat(rotation.requiresRotation(encryptedV2)).isFalse();
        assertThat(service.decrypt(encryptedV2, request)).isEqualTo("secret-value");
    }

    @Test
    void cachingProviderCachesPerOperationEvictsAndDestroysEntries() {
        AtomicInteger loads = new AtomicInteger();
        KeyProvider delegate = new KeyProvider() {
            @Override public KeyMaterial resolveEncryptionKey(KeyReference reference) {
                loads.incrementAndGet();
                return aesMaterial(reference.getAlias(), "v1", KeyPurpose.ENCRYPTION, (byte) 1);
            }
            @Override public KeyMaterial resolveDecryptionKey(KeyReference reference) {
                loads.incrementAndGet();
                return aesMaterial(reference.getAlias(), "v1", KeyPurpose.DECRYPTION, (byte) 1);
            }
        };
        var cache = new CachingKeyProvider(delegate, Duration.ofMinutes(1), 1);
        KeyReference one = KeyReference.builder().alias("one").purpose(KeyPurpose.ENCRYPTION)
            .algorithm(CryptoAlgorithm.AES_GCM_256).build();
        KeyMaterial first = cache.resolveEncryptionKey(one);
        assertThat(cache.resolveEncryptionKey(one)).isSameAs(first);
        assertThat(loads).hasValue(1);

        KeyReference two = KeyReference.builder().alias("two").purpose(KeyPurpose.ENCRYPTION)
            .algorithm(CryptoAlgorithm.AES_GCM_256).build();
        cache.resolveEncryptionKey(two);
        assertThat(first.isDestroyed()).isTrue();
        assertThat(loads).hasValue(2);
        cache.close();
        assertThatThrownBy(() -> cache.resolveEncryptionKey(null))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new CachingKeyProvider(delegate, Duration.ZERO, 1))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CachingKeyProvider(delegate, Duration.ofSeconds(1), 0))
            .isInstanceOf(IllegalArgumentException.class);
    }

    private static CryptoRequest request(
        CryptoAlgorithm algorithm, String alias, String version, byte[] aad
    ) {
        return CryptoRequest.builder().provider(CryptoProviderType.JCA)
            .algorithm(algorithm).keyAlias(alias).keyVersion(version)
            .additionalAuthenticatedData(aad).build();
    }

    private static KeyMaterial aesMaterial(
        String alias, String version, KeyPurpose purpose, byte fill
    ) {
        byte[] key = new byte[32];
        java.util.Arrays.fill(key, fill);
        return new KeyMaterial(alias, new KeyVersion(version, true), purpose,
            CryptoAlgorithm.AES_GCM_256, new SecretKeySpec(key, "AES"));
    }

    private static KeyMaterial rsaMaterial(Key key, KeyPurpose purpose) {
        return new KeyMaterial("rsa-key", new KeyVersion("v1", true), purpose,
            CryptoAlgorithm.RSA_OAEP_SHA256, key);
    }

    private static CipherEnvelope envelope(
        CryptoRequest request, KeyMaterial material,
        com.company.platform.logging.domain.model.CryptoResult result
    ) {
        return CipherEnvelope.builder().formatVersion("v1")
            .provider(request.getProvider()).algorithm(request.getAlgorithm())
            .keyAlias(material.alias()).keyVersion(material.version().getValue())
            .mode(result.getMode()).nonce(result.getNonce()).wrappedKey(result.getWrappedKey())
            .ciphertext(result.getCiphertext()).authenticationTag(result.getAuthenticationTag())
            .build();
    }

    private static CipherEnvelope copyEnvelope(
        CipherEnvelope source, byte[] ciphertext, byte[] tag
    ) {
        return CipherEnvelope.builder().formatVersion(source.getFormatVersion())
            .provider(source.getProvider()).algorithm(source.getAlgorithm())
            .keyAlias(source.getKeyAlias()).keyVersion(source.getKeyVersion())
            .mode(source.getMode()).nonce(source.getNonce()).wrappedKey(source.getWrappedKey())
            .ciphertext(ciphertext).authenticationTag(tag).build();
    }

    private static final class MutableAesKeyProvider implements KeyProvider {
        private String currentVersion = "v1";

        @Override public KeyMaterial resolveEncryptionKey(KeyReference reference) {
            return aesMaterial(reference.getAlias(), currentVersion,
                KeyPurpose.ENCRYPTION, versionByte(currentVersion));
        }

        @Override public KeyMaterial resolveDecryptionKey(KeyReference reference) {
            String version = reference.getVersion() == null ? currentVersion : reference.getVersion();
            return aesMaterial(reference.getAlias(), version,
                KeyPurpose.DECRYPTION, versionByte(version));
        }

        private static byte versionByte(String version) {
            return (byte) Integer.parseInt(version.substring(1));
        }
    }
}
