package com.company.platform.logging.domain.model;

import lombok.Builder;
import lombok.Getter;

import java.util.Arrays;
import java.util.Objects;

@Getter
public final class CryptoRequest {
    private final CryptoProviderType provider;
    private final CryptoAlgorithm algorithm;
    private final String keyAlias;
    private final String keyVersion;
    private final byte[] additionalAuthenticatedData;
    private final String strategyBean;

    @Builder
    public CryptoRequest(
        CryptoProviderType provider, CryptoAlgorithm algorithm, String keyAlias,
        String keyVersion, byte[] additionalAuthenticatedData, String strategyBean
    ) {
        this.provider = provider == null ? CryptoProviderType.JCA : provider;
        this.algorithm = algorithm == null ? CryptoAlgorithm.AES_GCM_256 : algorithm;
        this.keyAlias = Objects.requireNonNull(keyAlias, "keyAlias");
        this.keyVersion = keyVersion;
        this.additionalAuthenticatedData = additionalAuthenticatedData == null
            ? new byte[0] : additionalAuthenticatedData.clone();
        this.strategyBean = strategyBean;
    }

    public byte[] getAdditionalAuthenticatedData() {
        return additionalAuthenticatedData.clone();
    }

    @Override public String toString() {
        return "CryptoRequest(provider=" + provider + ", algorithm=" + algorithm
            + ", keyAlias=<redacted>, keyVersion=<redacted>, aadLength="
            + additionalAuthenticatedData.length + ")";
    }
}
