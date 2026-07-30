package com.company.platform.cache.domain.exception;

import com.company.platform.core.exception.PlatformInfrastructureException;

public final class PlatformCacheConfigurationException
    extends PlatformInfrastructureException {

    public PlatformCacheConfigurationException(String message) {
        super("CACHE.CONFIG.INVALID", message, null);
    }
}
