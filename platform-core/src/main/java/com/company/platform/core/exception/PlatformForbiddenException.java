package com.company.platform.core.exception;

import com.company.platform.core.exception.error.ErrorCategory;

public class PlatformForbiddenException extends PlatformException {
    public PlatformForbiddenException(String errorCode, String message) {
        super(builder(errorCode, ErrorCategory.AUTHORIZATION, message));
    }
}
