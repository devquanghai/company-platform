package com.company.platform.logging.domain.model;

import lombok.Builder;
import lombok.Getter;

import java.util.Objects;

@Getter
public final class KeyReference {
    private final String alias;
    private final String version;
    private final KeyPurpose purpose;
    private final CryptoAlgorithm algorithm;

    @Builder
    public KeyReference(
        String alias, String version, KeyPurpose purpose, CryptoAlgorithm algorithm
    ) {
        this.alias = Objects.requireNonNull(alias, "alias");
        this.version = version;
        this.purpose = Objects.requireNonNull(purpose, "purpose");
        this.algorithm = Objects.requireNonNull(algorithm, "algorithm");
    }

    @Override public String toString() {
        return "KeyReference(alias=<redacted>, version=<redacted>, purpose="
            + purpose + ", algorithm=" + algorithm + ")";
    }
}
