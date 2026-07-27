package com.company.platform.core.exception;

import com.company.platform.core.exception.error.ErrorCategory;

public class PlatformIntegrationException extends PlatformException {
    public PlatformIntegrationException(String errorCode, String message, Throwable cause) {
        super(builder(errorCode, ErrorCategory.INTEGRATION, message).cause(cause));
    }
}
