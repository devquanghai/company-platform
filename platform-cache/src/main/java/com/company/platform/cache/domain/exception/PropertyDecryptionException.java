package com.company.platform.cache.domain.exception;

import com.company.platform.core.exception.PlatformInfrastructureException;

/** @deprecated migrate property decryption to platform-logging. */
@Deprecated(forRemoval = true)
public final class PropertyDecryptionException extends PlatformInfrastructureException {
    public PropertyDecryptionException(Throwable cause) {
        super("PROPERTY.DECRYPTION.FAILED", "Property decryption failed", null);
    }
}
