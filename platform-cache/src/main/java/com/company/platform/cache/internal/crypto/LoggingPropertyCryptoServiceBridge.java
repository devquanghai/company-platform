package com.company.platform.cache.internal.crypto;

import com.company.platform.cache.api.crypto.PropertyCryptoService;

/** @deprecated compatibility bridge; use the platform-core API directly. */
@Deprecated(forRemoval = true)
@SuppressWarnings("removal")
public final class LoggingPropertyCryptoServiceBridge implements PropertyCryptoService {
    private final com.company.platform.core.crypto.api.PropertyCryptoService delegate;

    public LoggingPropertyCryptoServiceBridge(
        com.company.platform.core.crypto.api.PropertyCryptoService delegate
    ) {
        this.delegate = delegate;
    }

    @Override
    public String encrypt(String plainText) {
        return delegate.encrypt(plainText);
    }

    @Override
    public String encryptAndWrap(String plainText) {
        return delegate.encryptAndWrap(plainText);
    }

    @Override
    public String decrypt(String encryptedText) {
        return delegate.decrypt(encryptedText);
    }

    @Override
    public boolean isEncrypted(String value) {
        return delegate.isEncrypted(value);
    }
}
