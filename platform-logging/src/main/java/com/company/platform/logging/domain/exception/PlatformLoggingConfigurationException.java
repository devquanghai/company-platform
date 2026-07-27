package com.company.platform.logging.domain.exception;

import com.company.platform.core.exception.PlatformInfrastructureException;

public final class PlatformLoggingConfigurationException
    extends PlatformInfrastructureException {

    public PlatformLoggingConfigurationException(String detail) {
        super("PLATFORM.LOGGING.CONFIGURATION", detail, null);
    }
}
