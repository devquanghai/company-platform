package com.company.platform.core.crypto.internal.adapter.jasypt;

import com.company.platform.core.crypto.api.PropertyCryptoService;
import com.company.platform.core.exception.PlatformInfrastructureException;
import org.jasypt.encryption.StringEncryptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

public final class JasyptPropertyCryptoService implements PropertyCryptoService {
    private static final Logger LOG = LoggerFactory.getLogger("PLATFORM_CRYPTO");
    private static final String PREFIX = "ENC(";
    private static final String SUFFIX = ")";

    private final StringEncryptor encryptor;

    public JasyptPropertyCryptoService(StringEncryptor encryptor) {
        this.encryptor = Objects.requireNonNull(encryptor, "encryptor");
    }

    @Override
    public String encrypt(String plainText) {
        Objects.requireNonNull(plainText, "plainText");
        if (isEncrypted(plainText)) {
            return unwrap(plainText);
        }
        try {
            LOG.trace("property_crypto_started operation=ENCRYPT provider=JASYPT");
            String encrypted = encryptor.encrypt(plainText);
            LOG.debug("property_crypto_finished operation=ENCRYPT provider=JASYPT outcome=SUCCESS");
            return encrypted;
        } catch (RuntimeException failure) {
            LOG.debug("property_crypto_finished operation=ENCRYPT provider=JASYPT outcome=FAILED error_type={}",
                failure.getClass().getSimpleName());
            throw new PlatformInfrastructureException(
                "PROPERTY.ENCRYPTION.FAILED", "Property encryption failed", null);
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
            LOG.trace("property_crypto_started operation=DECRYPT provider=JASYPT");
            String decrypted = encryptor.decrypt(unwrap(encryptedText));
            LOG.debug("property_crypto_finished operation=DECRYPT provider=JASYPT outcome=SUCCESS");
            return decrypted;
        } catch (RuntimeException failure) {
            LOG.debug("property_crypto_finished operation=DECRYPT provider=JASYPT outcome=FAILED error_type={}",
                failure.getClass().getSimpleName());
            throw new PlatformInfrastructureException(
                "PROPERTY.DECRYPTION.FAILED", "Property decryption failed", null);
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
