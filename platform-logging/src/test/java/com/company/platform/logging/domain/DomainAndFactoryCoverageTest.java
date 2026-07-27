package com.company.platform.logging.domain;

import com.company.platform.logging.api.crypto.CryptoStrategy;
import com.company.platform.logging.api.logger.PlatformLogger;
import com.company.platform.logging.crypto.envelope.CipherEnvelopeCodec;
import com.company.platform.logging.crypto.factory.DefaultCryptoStrategyRegistry;
import com.company.platform.logging.crypto.factory.JasyptCryptoProviderFactory;
import com.company.platform.logging.crypto.factory.JcaCryptoProviderFactory;
import com.company.platform.logging.crypto.key.KeyResolver;
import com.company.platform.logging.domain.exception.PlatformCryptoException;
import com.company.platform.logging.domain.model.CipherEnvelope;
import com.company.platform.logging.domain.model.CryptoAlgorithm;
import com.company.platform.logging.domain.model.CryptoContext;
import com.company.platform.logging.domain.model.CryptoProviderType;
import com.company.platform.logging.domain.model.CryptoRequest;
import com.company.platform.logging.domain.model.CryptoResult;
import com.company.platform.logging.domain.model.KeyMaterial;
import com.company.platform.logging.domain.model.KeyPurpose;
import com.company.platform.logging.domain.model.KeyReference;
import com.company.platform.logging.domain.model.KeyVersion;
import com.company.platform.logging.domain.model.LogContext;
import com.company.platform.logging.domain.model.MaskingContext;
import com.company.platform.logging.domain.model.MaskingMatchType;
import com.company.platform.logging.domain.model.MaskingOutcome;
import com.company.platform.logging.domain.model.MaskingResult;
import com.company.platform.logging.domain.model.MaskingRule;
import com.company.platform.logging.domain.model.MaskingType;
import com.company.platform.logging.domain.model.PiiType;
import com.company.platform.logging.domain.model.SanitizedThrowable;
import com.company.platform.logging.structured.customizer.MutablePlatformLogEvent;
import com.company.platform.logging.structured.event.PlatformLogEvent;
import com.company.platform.logging.structured.event.PlatformLogField;
import com.company.platform.logging.domain.model.LogCategory;
import com.company.platform.logging.domain.model.LogSeverity;
import org.junit.jupiter.api.Test;

import javax.security.auth.DestroyFailedException;
import javax.security.auth.Destroyable;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DomainAndFactoryCoverageTest {

    @Test
    void cryptoModelsDefensivelyCopyAndRedactSecrets() {
        byte[] aad = {1, 2};
        CryptoRequest request = new CryptoRequest(null, null, "secret-alias",
            "v-secret", aad, "strategy");
        aad[0] = 9;
        assertThat(request.getProvider()).isEqualTo(CryptoProviderType.JCA);
        assertThat(request.getAlgorithm()).isEqualTo(CryptoAlgorithm.AES_GCM_256);
        assertThat(request.getAdditionalAuthenticatedData()).containsExactly(1, 2);
        byte[] copiedAad = request.getAdditionalAuthenticatedData();
        copiedAad[0] = 8;
        assertThat(request.getAdditionalAuthenticatedData()).containsExactly(1, 2);
        assertThat(request.toString()).doesNotContain("secret-alias", "v-secret");

        byte[] payload = {3, 4};
        CryptoResult result = new CryptoResult("DIRECT", null, null, payload, null);
        payload[0] = 7;
        assertThat(result.getNonce()).isEmpty();
        assertThat(result.getWrappedKey()).isEmpty();
        assertThat(result.getAuthenticationTag()).isEmpty();
        assertThat(result.getCiphertext()).containsExactly(3, 4);
        result.getCiphertext()[0] = 0;
        assertThat(result.getCiphertext()).containsExactly(3, 4);

        CipherEnvelope envelope = envelope();
        assertThat(envelope.canonicalHeader())
            .isEqualTo("v1|JCA|AES_GCM_256|alias|v1|DIRECT");
        assertThat(envelope.getNonce()).containsExactly(1);
        assertThat(envelope.getWrappedKey()).containsExactly(2);
        assertThat(envelope.getCiphertext()).containsExactly(3);
        assertThat(envelope.getAuthenticationTag()).containsExactly(4);
        assertThat(envelope.toString()).doesNotContain("alias").contains("payloadLength=1");
    }

    @Test
    void cryptoContextBuildsAuthenticatedHeaderAndExtraData() {
        CryptoRequest request = CryptoRequest.builder()
            .keyAlias("alias").additionalAuthenticatedData(new byte[]{9, 8}).build();
        KeyMaterial key = keyMaterial(new TestKey(false));
        CryptoContext context = new CryptoContext(request, key, null);

        byte[] authenticated = context.authenticatedData("DIRECT");
        assertThat(new String(authenticated, StandardCharsets.UTF_8))
            .startsWith("v1|JCA|AES_GCM_256|alias|v1|DIRECT")
            .endsWith(new String(new byte[]{0, 9, 8}, StandardCharsets.ISO_8859_1));
        assertThat(context.getRequest()).isSameAs(request);
        assertThat(context.getKeyMaterial()).isSameAs(key);
        assertThat(context.getEnvelope()).isNull();
    }

    @Test
    void keyModelsExposeLifecycleWithoutLeakingIdentifiers() {
        TestKey key = new TestKey(false);
        KeyMaterial material = keyMaterial(key);
        assertThat(material.alias()).isEqualTo("alias");
        assertThat(material.version().getValue()).isEqualTo("v1");
        assertThat(material.version().isActive()).isTrue();
        assertThat(material.purpose()).isEqualTo(KeyPurpose.ENCRYPTION);
        assertThat(material.algorithm()).isEqualTo(CryptoAlgorithm.AES_GCM_256);
        assertThat(material.key()).isSameAs(key);
        assertThat(material.toString())
            .contains("alias=<redacted>", "version=<redacted>")
            .doesNotContain("version=v1");
        material.close();
        material.close();
        assertThat(material.isDestroyed()).isTrue();
        assertThat(key.destroyed).isTrue();
        assertThatThrownBy(material::key).isInstanceOf(IllegalStateException.class);

        KeyMaterial failedDestroy = keyMaterial(new TestKey(true));
        failedDestroy.close();
        assertThat(failedDestroy.isDestroyed()).isTrue();

        KeyReference reference = new KeyReference(
            "sensitive", "secret-version", KeyPurpose.DECRYPTION,
            CryptoAlgorithm.RSA_OAEP_SHA256);
        assertThat(reference.getAlias()).isEqualTo("sensitive");
        assertThat(reference.getVersion()).isEqualTo("secret-version");
        assertThat(reference.toString()).doesNotContain("sensitive", "secret-version");
        assertThat(new KeyVersion("hidden", false).toString()).doesNotContain("hidden");
    }

    @Test
    void maskingAndLoggingModelsCoverDefaultsCopiesAndReservedFields() {
        assertThat(MaskingResult.masked("***").getOutcome()).isEqualTo(MaskingOutcome.MASKED);
        assertThat(MaskingResult.removed().getOutcome()).isEqualTo(MaskingOutcome.REMOVED);
        assertThat(MaskingResult.removed().getValue()).isNull();
        assertThat(MaskingResult.unchanged("safe").getOutcome())
            .isEqualTo(MaskingOutcome.UNCHANGED);

        MaskingRule rule = new MaskingRule(
            "rule", true, MaskingMatchType.FIELD_NAME, null, null, null,
            1, 2, null, true, "bean");
        assertThat(rule.getExpressions()).isEmpty();
        assertThat(rule.getPiiType()).isEqualTo(PiiType.GENERIC);
        assertThat(rule.getMaskingType()).isEqualTo(MaskingType.SUBSTITUTION);
        assertThat(rule.getSubstitution()).isEqualTo("***");

        MaskingContext context = MaskingContext.builder()
            .fieldName("email").piiType(PiiType.EMAIL).build();
        assertThat(context.getSubstitution()).isEqualTo("***");
        assertThat(LogContext.builder().values(null).build().getValues()).isEmpty();

        OffsetDateTime timestamp = OffsetDateTime.parse("2025-01-01T00:00:00Z");
        PlatformLogEvent event = new PlatformLogEvent(
            "event", "message", LogSeverity.INFO, LogCategory.APPLICATION,
            timestamp, Map.of("valid", 1));
        assertThat(event.getEventName()).isEqualTo("event");
        assertThat(event.getMessage()).isEqualTo("message");
        assertThat(event.getSeverity()).isEqualTo(LogSeverity.INFO);
        assertThat(event.getCategory()).isEqualTo(LogCategory.APPLICATION);
        assertThat(event.getTimestamp()).isEqualTo(timestamp);
        assertThat(event.getFields()).containsEntry("valid", 1);

        MutablePlatformLogEvent mutable = new MutablePlatformLogEvent(
            "event", "message", LogSeverity.DEBUG, LogCategory.PERFORMANCE,
            Map.of("event.name", "ignored", "safe", "value"));
        mutable.put("event.message", "ignored");
        mutable.put(null, "ignored");
        mutable.put("null", null);
        mutable.put("added", 2);
        assertThat(mutable.fieldsSnapshot()).containsOnlyKeys("safe", "added");
        assertThat(new PlatformLogField("name", null).getValue()).isNull();
        assertThatThrownBy(() -> new PlatformLogField(null, "value"))
            .isInstanceOf(NullPointerException.class);

        SanitizedThrowable throwable = SanitizedThrowable.builder()
            .type("type").message("safe").stackTrace(null).suppressed(null).build();
        assertThat(throwable.getStackTrace()).isEmpty();
        assertThat(throwable.getSuppressed()).isEmpty();
        assertThat(throwable.getCause()).isNull();
    }

    @Test
    void strategyRegistryAndFactoriesResolveAndRejectUnsupportedAlgorithms() {
        StubStrategy aes = new StubStrategy(CryptoProviderType.JCA,
            CryptoAlgorithm.AES_GCM_256);
        StubStrategy pbe = new StubStrategy(CryptoProviderType.JASYPT, CryptoAlgorithm.PBE);
        DefaultCryptoStrategyRegistry registry =
            new DefaultCryptoStrategyRegistry(Map.of("aesBean", aes, "pbeBean", pbe));
        assertThat(registry.resolve(CryptoProviderType.JCA, CryptoAlgorithm.AES_GCM_256))
            .isSameAs(aes);
        assertThat(registry.find("aesBean")).contains(aes);
        assertThat(registry.find("missing")).isEmpty();
        assertThatThrownBy(() ->
            registry.resolve(CryptoProviderType.CUSTOM, CryptoAlgorithm.CUSTOM))
            .isInstanceOf(PlatformCryptoException.class);

        KeyResolver keys = new EmptyKeys();
        CipherEnvelopeCodec codec = new IdentityCodec();
        JcaCryptoProviderFactory jca = new JcaCryptoProviderFactory(
            Map.of(CryptoAlgorithm.AES_GCM_256, aes), keys, codec);
        assertThat(jca.providerType()).isEqualTo(CryptoProviderType.JCA);
        assertThat(jca.createStrategy(CryptoAlgorithm.AES_GCM_256)).isSameAs(aes);
        assertThat(jca.createKeyResolver()).isSameAs(keys);
        assertThat(jca.createEnvelopeCodec()).isSameAs(codec);
        assertThatThrownBy(() -> jca.createStrategy(CryptoAlgorithm.CUSTOM))
            .isInstanceOf(PlatformCryptoException.class);

        JasyptCryptoProviderFactory jasypt =
            new JasyptCryptoProviderFactory(pbe, keys, codec);
        assertThat(jasypt.providerType()).isEqualTo(CryptoProviderType.JASYPT);
        assertThat(jasypt.createStrategy(CryptoAlgorithm.PBE)).isSameAs(pbe);
        assertThat(jasypt.createKeyResolver()).isSameAs(keys);
        assertThat(jasypt.createEnvelopeCodec()).isSameAs(codec);
        assertThatThrownBy(() -> jasypt.createStrategy(CryptoAlgorithm.AES_GCM_256))
            .isInstanceOf(PlatformCryptoException.class);
    }

    @Test
    void platformLoggerDefaultDispatchesEverySeverity() {
        DispatchLogger logger = new DispatchLogger();
        for (LogSeverity severity : LogSeverity.values()) {
            logger.log(severity, LogCategory.SECURITY, "event", "message",
                Map.of(), new IllegalStateException());
        }
        assertThat(logger.trace).isEqualTo(1);
        assertThat(logger.debug).isEqualTo(1);
        assertThat(logger.info).isEqualTo(1);
        assertThat(logger.warn).isEqualTo(1);
        assertThat(logger.error).isEqualTo(1);

        StubStrategy strategy = new StubStrategy(
            CryptoProviderType.JCA, CryptoAlgorithm.AES_GCM_256);
        CryptoResult result = strategy.encryptResult(new byte[]{1, 2},
            new CryptoContext(CryptoRequest.builder().keyAlias("alias").build(),
                keyMaterial(new TestKey(false)), null));
        assertThat(result.getMode()).isEqualTo("DIRECT");
        assertThat(result.getCiphertext()).containsExactly(1, 2);
    }

    private static KeyMaterial keyMaterial(Key key) {
        return new KeyMaterial("alias", new KeyVersion("v1", true),
            KeyPurpose.ENCRYPTION, CryptoAlgorithm.AES_GCM_256, key);
    }

    private static CipherEnvelope envelope() {
        return new CipherEnvelope("v1", CryptoProviderType.JCA,
            CryptoAlgorithm.AES_GCM_256, "alias", "v1", "DIRECT",
            new byte[]{1}, new byte[]{2}, new byte[]{3}, new byte[]{4});
    }

    private static final class StubStrategy implements CryptoStrategy {
        private final CryptoProviderType provider;
        private final CryptoAlgorithm algorithm;
        private StubStrategy(CryptoProviderType provider, CryptoAlgorithm algorithm) {
            this.provider = provider;
            this.algorithm = algorithm;
        }
        @Override public CryptoProviderType provider() { return provider; }
        @Override public CryptoAlgorithm algorithm() { return algorithm; }
        @Override public byte[] encrypt(byte[] plaintext, CryptoContext context) {
            return plaintext.clone();
        }
        @Override public byte[] decrypt(byte[] ciphertext, CryptoContext context) {
            return ciphertext.clone();
        }
    }

    private static final class EmptyKeys implements KeyResolver {
        @Override public KeyMaterial resolveEncryptionKey(KeyReference reference) { return null; }
        @Override public KeyMaterial resolveDecryptionKey(KeyReference reference) { return null; }
    }

    private static final class IdentityCodec implements CipherEnvelopeCodec {
        @Override public String encode(CipherEnvelope envelope) {
            return envelope.canonicalHeader();
        }
        @Override public CipherEnvelope decode(String encoded) { return envelope(); }
    }

    private static final class TestKey implements Key, Destroyable {
        private final boolean fail;
        private boolean destroyed;
        private TestKey(boolean fail) { this.fail = fail; }
        @Override public String getAlgorithm() { return "AES"; }
        @Override public String getFormat() { return "RAW"; }
        @Override public byte[] getEncoded() { return new byte[]{1}; }
        @Override public void destroy() throws DestroyFailedException {
            if (fail) {
                throw new DestroyFailedException();
            }
            destroyed = true;
        }
        @Override public boolean isDestroyed() { return destroyed; }
    }

    private static final class DispatchLogger implements PlatformLogger {
        private int trace;
        private int debug;
        private int info;
        private int warn;
        private int error;
        @Override public void trace(String eventName, String message, Map<String, ?> fields) {
            trace++;
        }
        @Override public void debug(String eventName, String message, Map<String, ?> fields) {
            debug++;
        }
        @Override public void info(String eventName, String message, Map<String, ?> fields) {
            info++;
        }
        @Override public void warn(String eventName, String message, Map<String, ?> fields) {
            warn++;
        }
        @Override public void error(
            String eventName, String message, Map<String, ?> fields, Throwable throwable
        ) {
            error++;
        }
    }
}
