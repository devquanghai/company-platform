package com.company.platform.logging.crypto.internal.application;

import com.company.platform.logging.api.crypto.CryptoRotationService;
import com.company.platform.logging.api.crypto.CryptoService;
import com.company.platform.logging.api.crypto.KeyProvider;
import com.company.platform.logging.crypto.envelope.CipherEnvelopeCodec;
import com.company.platform.logging.domain.model.CipherEnvelope;
import com.company.platform.logging.domain.model.CryptoRequest;
import com.company.platform.logging.domain.model.KeyPurpose;
import com.company.platform.logging.domain.model.KeyReference;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final class DefaultCryptoRotationService implements CryptoRotationService {
    private final CipherEnvelopeCodec envelopes;
    private final KeyProvider keys;
    private final CryptoService crypto;

    public DefaultCryptoRotationService(
        CipherEnvelopeCodec envelopes, KeyProvider keys, CryptoService crypto
    ) {
        this.envelopes = envelopes;
        this.keys = keys;
        this.crypto = crypto;
    }

    @Override
    public boolean requiresRotation(String ciphertext) {
        CipherEnvelope envelope = envelopes.decode(ciphertext);
        var active = keys.resolveEncryptionKey(KeyReference.builder()
            .alias(envelope.getKeyAlias()).purpose(KeyPurpose.ENCRYPTION)
            .algorithm(envelope.getAlgorithm()).build());
        return !active.version().getValue().equals(envelope.getKeyVersion());
    }

    @Override
    public String rotate(String ciphertext) {
        CipherEnvelope envelope = envelopes.decode(ciphertext);
        CryptoRequest request = CryptoRequest.builder().provider(envelope.getProvider())
            .algorithm(envelope.getAlgorithm()).keyAlias(envelope.getKeyAlias()).build();
        byte[] encoded = ciphertext.getBytes(StandardCharsets.UTF_8);
        byte[] plaintext = null;
        byte[] rotated = null;
        try {
            plaintext = crypto.decrypt(encoded, request);
            rotated = crypto.encrypt(plaintext, request);
            return new String(rotated, StandardCharsets.UTF_8);
        } finally {
            Arrays.fill(encoded, (byte) 0);
            if (plaintext != null) {
                Arrays.fill(plaintext, (byte) 0);
            }
            if (rotated != null) {
                Arrays.fill(rotated, (byte) 0);
            }
        }
    }
}
