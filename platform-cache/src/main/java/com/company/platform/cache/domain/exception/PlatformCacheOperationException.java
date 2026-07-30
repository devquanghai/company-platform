package com.company.platform.cache.domain.exception;

import com.company.platform.core.exception.PlatformInfrastructureException;

public final class PlatformCacheOperationException extends PlatformInfrastructureException {
    public PlatformCacheOperationException(String code, String message, Throwable cause) {
        super(code, message, cause);
    }
}
