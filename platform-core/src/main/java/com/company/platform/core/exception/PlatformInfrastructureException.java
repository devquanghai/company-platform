package com.company.platform.core.exception;

import com.company.platform.core.exception.error.ErrorCategory;

public class PlatformInfrastructureException extends PlatformException {
    public PlatformInfrastructureException(String errorCode, String message, Throwable cause) {
        super(builder(errorCode, ErrorCategory.INFRASTRUCTURE, message).cause(cause));
    }
}
