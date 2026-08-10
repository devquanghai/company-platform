package com.company.platform.logging.crypto.internal.application;

import com.company.platform.logging.api.crypto.CryptoService;
import com.company.platform.logging.api.crypto.CryptoStrategy;
import com.company.platform.logging.api.crypto.CryptoStrategyRegistry;
import com.company.platform.logging.api.crypto.KeyProvider;
import com.company.platform.logging.audit.event.CryptoOperationEvent;
import com.company.platform.logging.audit.publisher.LoggingAuditEventPublisher;
import com.company.platform.logging.crypto.envelope.CipherEnvelopeCodec;
import com.company.platform.logging.domain.exception.PlatformCryptoException;
import com.company.platform.logging.domain.model.CipherEnvelope;
import com.company.platform.logging.domain.model.CryptoContext;
import com.company.platform.logging.domain.model.CryptoRequest;
import com.company.platform.logging.domain.model.CryptoResult;
import com.company.platform.logging.domain.model.KeyMaterial;
import com.company.platform.logging.domain.model.KeyPurpose;
import com.company.platform.logging.domain.model.KeyReference;
import com.company.platform.logging.domain.model.CryptoOperation;
import com.company.platform.logging.observability.metrics.LoggingMetrics;
import com.company.platform.core.context.RequestContextProvider;
import com.company.platform.core.time.TimeProvider;
import com.company.platform.core.trace.TraceContextProvider;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.time.Duration;
import java.time.OffsetDateTime;

public final class DefaultCryptoService implements CryptoService {
    private final CryptoStrategyRegistry strategies;
    private final KeyProvider keys;
    private final CipherEnvelopeCodec envelopes;
    private final LoggingAuditEventPublisher audit;
    private final LoggingMetrics metrics;
    private final TimeProvider time;
    private final RequestContextProvider requests;
    private final TraceContextProvider traces;

    public DefaultCryptoService(
        CryptoStrategyRegistry strategies, KeyProvider keys,
        CipherEnvelopeCodec envelopes
    ) {
        this(strategies, keys, envelopes, null, null, null, null, null);
    }

    public DefaultCryptoService(
        CryptoStrategyRegistry strategies, KeyProvider keys,
        CipherEnvelopeCodec envelopes, LoggingAuditEventPublisher audit,
        LoggingMetrics metrics, TimeProvider time,
        RequestContextProvider requests, TraceContextProvider traces
    ) {
        this.strategies = strategies;
        this.keys = keys;
        this.envelopes = envelopes;
        this.audit = audit;
        this.metrics = metrics;
        this.time = time;
        this.requests = requests;
        this.traces = traces;
    }

    @Override
    public String encrypt(String plaintext, CryptoRequest request) {
        if (plaintext == null) {
            throw failure("plaintext must not be null");
        }
        byte[] value = plaintext.getBytes(StandardCharsets.UTF_8);
        try {
            return new String(encrypt(value, request), StandardCharsets.UTF_8);
        } finally {
            Arrays.fill(value, (byte) 0);
        }
    }

    @Override
    public String decrypt(String ciphertext, CryptoRequest request) {
        if (ciphertext == null) {
            throw failure("ciphertext must not be null");
        }
        byte[] value = ciphertext.getBytes(StandardCharsets.UTF_8);
        byte[] plaintext = null;
        try {
            plaintext = decrypt(value, request);
            return decodeUtf8(plaintext);
        } finally {
            Arrays.fill(value, (byte) 0);
            if (plaintext != null) {
                Arrays.fill(plaintext, (byte) 0);
            }
        }
    }

    @Override
    public byte[] encrypt(byte[] plaintext, CryptoRequest request) {
        long started = System.nanoTime();
        try {
            byte[] result = encryptInternal(plaintext, request);
            observe(CryptoOperation.ENCRYPT, request, started, null);
            return result;
        } catch (RuntimeException exception) {
            observe(CryptoOperation.ENCRYPT, request, started, exception);
            throw exception;
        }
    }

    private byte[] encryptInternal(byte[] plaintext, CryptoRequest request) {
        require(plaintext, request);
        KeyReference reference = KeyReference.builder().alias(request.getKeyAlias())
            .version(request.getKeyVersion()).purpose(KeyPurpose.ENCRYPTION)
            .algorithm(request.getAlgorithm()).build();
        KeyMaterial material = keys.resolveEncryptionKey(reference);
        validateMaterial(material, request, KeyPurpose.ENCRYPTION);
        CryptoContext context = new CryptoContext(request, material, null);
        CryptoStrategy strategy = strategy(request);
        CryptoResult result = strategy.encryptResult(plaintext.clone(), context);
        CipherEnvelope envelope = CipherEnvelope.builder().formatVersion("v1")
            .provider(request.getProvider()).algorithm(request.getAlgorithm())
            .keyAlias(material.alias()).keyVersion(material.version().getValue())
            .mode(result.getMode()).nonce(result.getNonce())
            .wrappedKey(result.getWrappedKey()).ciphertext(result.getCiphertext())
            .authenticationTag(result.getAuthenticationTag()).build();
        return envelopes.encode(envelope).getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public byte[] decrypt(byte[] ciphertext, CryptoRequest request) {
        long started = System.nanoTime();
        try {
            byte[] result = decryptInternal(ciphertext, request);
            observe(CryptoOperation.DECRYPT, request, started, null);
            return result;
        } catch (RuntimeException exception) {
            observe(CryptoOperation.DECRYPT, request, started, exception);
            throw exception;
        }
    }

    private byte[] decryptInternal(byte[] ciphertext, CryptoRequest request) {
        require(ciphertext, request);
        CipherEnvelope envelope = envelopes.decode(decodeUtf8(ciphertext));
        validateEnvelope(envelope, request);
        KeyReference reference = KeyReference.builder().alias(envelope.getKeyAlias())
            .version(envelope.getKeyVersion()).purpose(KeyPurpose.DECRYPTION)
            .algorithm(envelope.getAlgorithm()).build();
        KeyMaterial material = keys.resolveDecryptionKey(reference);
        validateMaterial(material, request, KeyPurpose.DECRYPTION);
        CryptoContext context = new CryptoContext(request, material, envelope);
        return strategy(request).decrypt(envelope.getCiphertext(), context);
    }

    private void observe(
        CryptoOperation operation, CryptoRequest request,
        long started, RuntimeException failure
    ) {
        if (request == null) {
            return;
        }
        Duration duration = Duration.ofNanos(System.nanoTime() - started);
        if (metrics != null) {
            try {
                metrics.recordCrypto(request.getProvider(), request.getAlgorithm(),
                    operation, failure == null, duration);
            } catch (RuntimeException ignored) {
                // Observability is fail-open and must never change crypto semantics.
            }
        }
        if (audit != null) {
            try {
                var trace = traces == null ? null : traces.getCurrentContext();
                String traceId = trace == null ? null : trace.getTraceId();
                audit.publish(new CryptoOperationEvent(
                    time == null ? OffsetDateTime.now() : time.now(),
                    operation.name(), failure == null ? "SUCCESS" : "FAILED",
                    request.getProvider().name(), request.getAlgorithm().name(), duration,
                    failure == null ? null : failure.getClass().getName(),
                    traceId,
                    requests == null ? null : requests.getRequestId()));
            } catch (RuntimeException ignored) {
                // Audit transport is fail-open here; crypto result remains authoritative.
            }
        }
    }

    private CryptoStrategy strategy(CryptoRequest request) {
        if (request.getStrategyBean() != null && !request.getStrategyBean().isBlank()) {
            return strategies.find(request.getStrategyBean()).orElseThrow(
                () -> new PlatformCryptoException(
                    "PLATFORM.CRYPTO.STRATEGY_UNAVAILABLE",
                    "Named crypto strategy is unavailable"));
        }
        return strategies.resolve(request.getProvider(), request.getAlgorithm());
    }

    private static void validateEnvelope(
        CipherEnvelope envelope, CryptoRequest request
    ) {
        if (envelope.getProvider() != request.getProvider()
            || envelope.getAlgorithm() != request.getAlgorithm()
            || !envelope.getKeyAlias().equals(request.getKeyAlias())
            || request.getKeyVersion() != null
                && !request.getKeyVersion().equals(envelope.getKeyVersion())) {
            throw failure("cipher envelope does not match the requested policy");
        }
    }

    private static void validateMaterial(
        KeyMaterial material, CryptoRequest request, KeyPurpose purpose
    ) {
        if (!material.alias().equals(request.getKeyAlias())
            || material.algorithm() != request.getAlgorithm()
            || material.purpose() != purpose
            || request.getKeyVersion() != null
                && !request.getKeyVersion().equals(material.version().getValue())) {
            throw failure("resolved key does not match the requested policy");
        }
    }

    private static void require(byte[] value, CryptoRequest request) {
        if (value == null || request == null || request.getKeyAlias().isBlank()) {
            throw failure("crypto input and request are required");
        }
    }

    private static String decodeUtf8(byte[] value) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(value)).toString();
        } catch (CharacterCodingException exception) {
            throw failure("cipher envelope is not valid UTF-8");
        }
    }

    private static PlatformCryptoException failure(String detail) {
        return new PlatformCryptoException("PLATFORM.CRYPTO.OPERATION", detail);
    }
}
