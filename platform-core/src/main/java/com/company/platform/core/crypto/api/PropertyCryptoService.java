package com.company.platform.core.crypto.api;

/**
 * Encrypts and decrypts values stored in application properties.
 */
public interface PropertyCryptoService {
    String encrypt(String plainText);

    String encryptAndWrap(String plainText);

    String decrypt(String encryptedText);

    boolean isEncrypted(String value);
}
