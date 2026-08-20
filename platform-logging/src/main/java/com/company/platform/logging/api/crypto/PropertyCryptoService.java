package com.company.platform.logging.api.crypto;

/**
 * @deprecated use {@link com.company.platform.core.crypto.api.PropertyCryptoService};
 * this compatibility contract will be removed in the next major version.
 */
@Deprecated(forRemoval = true)
public interface PropertyCryptoService {
    String encrypt(String plainText);

    String encryptAndWrap(String plainText);

    String decrypt(String encryptedText);

    boolean isEncrypted(String value);
}
