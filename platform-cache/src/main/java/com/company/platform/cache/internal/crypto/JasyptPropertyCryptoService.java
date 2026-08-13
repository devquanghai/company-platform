package com.company.platform.cache.internal.crypto;

import com.company.platform.cache.api.crypto.PropertyCryptoService;
import com.company.platform.cache.domain.exception.PropertyDecryptionException;
import com.company.platform.cache.domain.exception.PropertyEncryptionException;
import org.jasypt.encryption.StringEncryptor;

import java.util.Objects;

public final class JasyptPropertyCryptoService implements PropertyCryptoService {
    private static final String PREFIX = "ENC(";
    private static final String SUFFIX = ")";

    private final StringEncryptor stringEncryptor;

    public JasyptPropertyCryptoService(StringEncryptor stringEncryptor) {
        this.stringEncryptor = Objects.requireNonNull(stringEncryptor, "stringEncryptor");
    }

    @Override
    public String encrypt(String plainText) {
        Objects.requireNonNull(plainText, "plainText");
        if (isEncrypted(plainText)) {
            return unwrap(plainText);
        }
        try {
            return stringEncryptor.encrypt(plainText);
        } catch (RuntimeException exception) {
            throw new PropertyEncryptionException(exception);
        }
    }

    @Override
    public String encryptAndWrap(String plainText) {
        Objects.requireNonNull(plainText, "plainText");
        return isEncrypted(plainText) ? plainText : PREFIX + encrypt(plainText) + SUFFIX;
    }

    @Override
    public String decrypt(String encryptedText) {
        Objects.requireNonNull(encryptedText, "encryptedText");
        try {
            return stringEncryptor.decrypt(unwrap(encryptedText));
        } catch (RuntimeException exception) {
            throw new PropertyDecryptionException(exception);
        }
    }

    @Override
    public boolean isEncrypted(String value) {
        return value != null && value.startsWith(PREFIX) && value.endsWith(SUFFIX);
    }

    private String unwrap(String value) {
        return isEncrypted(value)
            ? value.substring(PREFIX.length(), value.length() - SUFFIX.length())
            : value;
    }
}
