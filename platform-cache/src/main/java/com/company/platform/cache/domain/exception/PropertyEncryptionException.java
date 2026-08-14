package com.company.platform.cache.domain.exception;

import com.company.platform.core.exception.PlatformInfrastructureException;

/** @deprecated migrate property encryption to platform-logging. */
@Deprecated(forRemoval = true)
public final class PropertyEncryptionException extends PlatformInfrastructureException {
    public PropertyEncryptionException(Throwable cause) {
        super("PROPERTY.ENCRYPTION.FAILED", "Property encryption failed", null);
    }
}
