package com.company.platform.core.exception;

import com.company.platform.core.exception.error.ErrorCategory;

public class PlatformTimeoutException extends PlatformException {
    public PlatformTimeoutException(String errorCode, String message, Throwable cause) {
        super(builder(errorCode, ErrorCategory.TIMEOUT, message).cause(cause));
    }
}
