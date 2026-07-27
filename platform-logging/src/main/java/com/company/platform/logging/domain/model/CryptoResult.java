package com.company.platform.logging.domain.model;

import lombok.Builder;
import lombok.Getter;

@Getter
public final class CryptoResult {
    private final String mode;
    private final byte[] nonce;
    private final byte[] wrappedKey;
    private final byte[] ciphertext;
    private final byte[] authenticationTag;

    @Builder
    public CryptoResult(
        String mode, byte[] nonce, byte[] wrappedKey,
        byte[] ciphertext, byte[] authenticationTag
    ) {
        this.mode = mode;
        this.nonce = copy(nonce);
        this.wrappedKey = copy(wrappedKey);
        this.ciphertext = copy(ciphertext);
        this.authenticationTag = copy(authenticationTag);
    }

    public byte[] getNonce() { return copy(nonce); }
    public byte[] getWrappedKey() { return copy(wrappedKey); }
    public byte[] getCiphertext() { return copy(ciphertext); }
    public byte[] getAuthenticationTag() { return copy(authenticationTag); }
    private static byte[] copy(byte[] value) { return value == null ? new byte[0] : value.clone(); }
}
