package com.company.platform.logging.crypto.key;

import com.company.platform.logging.domain.exception.PlatformCryptoException;
import com.company.platform.logging.domain.model.KeyMaterial;
import com.company.platform.logging.domain.model.KeyReference;

public final class RejectingKeyProvider implements KeyResolver {
    @Override public KeyMaterial resolveEncryptionKey(KeyReference reference) {
        throw unavailable();
    }
    @Override public KeyMaterial resolveDecryptionKey(KeyReference reference) {
        throw unavailable();
    }
    private static PlatformCryptoException unavailable() {
        return new PlatformCryptoException(
            "PLATFORM.CRYPTO.KEY_PROVIDER_UNAVAILABLE",
            "No external key provider is configured");
    }
}
