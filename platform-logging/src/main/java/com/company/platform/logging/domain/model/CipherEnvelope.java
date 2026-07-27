package com.company.platform.logging.domain.model;

import lombok.Builder;
import lombok.Getter;

import java.util.Objects;

@Getter
public final class CipherEnvelope {
    private final String formatVersion;
    private final CryptoProviderType provider;
    private final CryptoAlgorithm algorithm;
    private final String keyAlias;
    private final String keyVersion;
    private final String mode;
    private final byte[] nonce;
    private final byte[] wrappedKey;
    private final byte[] ciphertext;
    private final byte[] authenticationTag;

    @Builder
    public CipherEnvelope(
        String formatVersion, CryptoProviderType provider, CryptoAlgorithm algorithm,
        String keyAlias, String keyVersion, String mode, byte[] nonce,
        byte[] wrappedKey, byte[] ciphertext, byte[] authenticationTag
    ) {
        this.formatVersion = Objects.requireNonNull(formatVersion, "formatVersion");
        this.provider = Objects.requireNonNull(provider, "provider");
        this.algorithm = Objects.requireNonNull(algorithm, "algorithm");
        this.keyAlias = Objects.requireNonNull(keyAlias, "keyAlias");
        this.keyVersion = Objects.requireNonNull(keyVersion, "keyVersion");
        this.mode = Objects.requireNonNull(mode, "mode");
        this.nonce = copy(nonce);
        this.wrappedKey = copy(wrappedKey);
        this.ciphertext = copy(ciphertext);
        this.authenticationTag = copy(authenticationTag);
    }

    public byte[] getNonce() { return copy(nonce); }
    public byte[] getWrappedKey() { return copy(wrappedKey); }
    public byte[] getCiphertext() { return copy(ciphertext); }
    public byte[] getAuthenticationTag() { return copy(authenticationTag); }

    public String canonicalHeader() {
        return canonicalHeader(formatVersion, provider, algorithm, keyAlias, keyVersion, mode);
    }

    public static String canonicalHeader(
        String formatVersion, CryptoProviderType provider, CryptoAlgorithm algorithm,
        String alias, String version, String mode
    ) {
        return formatVersion + "|" + provider.name() + "|" + algorithm.name()
            + "|" + alias + "|" + version + "|" + mode;
    }

    private static byte[] copy(byte[] value) { return value == null ? new byte[0] : value.clone(); }

    @Override public String toString() {
        return "CipherEnvelope(version=" + formatVersion + ", provider=" + provider
            + ", algorithm=" + algorithm + ", key=<redacted>, mode=" + mode
            + ", payloadLength=" + ciphertext.length + ")";
    }
}
