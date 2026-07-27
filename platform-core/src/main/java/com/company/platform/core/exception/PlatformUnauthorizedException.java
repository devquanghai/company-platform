package com.company.platform.core.exception;

import com.company.platform.core.exception.error.ErrorCategory;

public class PlatformUnauthorizedException extends PlatformException {
    public PlatformUnauthorizedException(String errorCode, String message) {
        super(builder(errorCode, ErrorCategory.AUTHENTICATION, message));
    }
}
