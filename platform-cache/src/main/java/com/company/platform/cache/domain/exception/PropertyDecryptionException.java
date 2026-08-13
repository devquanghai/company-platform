package com.company.platform.cache.domain.exception;

import com.company.platform.core.exception.PlatformInfrastructureException;

public final class PropertyDecryptionException extends PlatformInfrastructureException {
    public PropertyDecryptionException(Throwable cause) {
        super("PROPERTY.DECRYPTION.FAILED", "Property decryption failed", cause);
    }
}
