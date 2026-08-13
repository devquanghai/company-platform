package com.company.platform.cache.domain.exception;

import com.company.platform.core.exception.PlatformInfrastructureException;

public final class PropertyEncryptionException extends PlatformInfrastructureException {
    public PropertyEncryptionException(Throwable cause) {
        super("PROPERTY.ENCRYPTION.FAILED", "Property encryption failed", cause);
    }
}
