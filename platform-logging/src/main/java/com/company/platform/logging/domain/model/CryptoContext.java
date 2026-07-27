package com.company.platform.logging.domain.model;

import lombok.Getter;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

@Getter
public final class CryptoContext {
    private final CryptoRequest request;
    private final KeyMaterial keyMaterial;
    private final CipherEnvelope envelope;

    public CryptoContext(
        CryptoRequest request, KeyMaterial keyMaterial, CipherEnvelope envelope
    ) {
        this.request = request;
        this.keyMaterial = keyMaterial;
        this.envelope = envelope;
    }

    public byte[] authenticatedData(String mode) {
        String version = keyMaterial.version().getValue();
        String header = CipherEnvelope.canonicalHeader(
            "v1", request.getProvider(), request.getAlgorithm(),
            keyMaterial.alias(), version, mode);
        byte[] standard = header.getBytes(StandardCharsets.UTF_8);
        byte[] extra = request.getAdditionalAuthenticatedData();
        byte[] combined = Arrays.copyOf(standard, standard.length + 1 + extra.length);
        combined[standard.length] = 0;
        System.arraycopy(extra, 0, combined, standard.length + 1, extra.length);
        return combined;
    }
}
