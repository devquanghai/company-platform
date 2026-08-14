package com.company.platform.logging.api.crypto;

public interface PropertyCryptoService {
    String encrypt(String plainText);

    String encryptAndWrap(String plainText);

    String decrypt(String encryptedText);

    boolean isEncrypted(String value);
}
