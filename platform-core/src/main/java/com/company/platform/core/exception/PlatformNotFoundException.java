package com.company.platform.core.exception;

import com.company.platform.core.exception.error.ErrorCategory;

public class PlatformNotFoundException extends PlatformException {
    public PlatformNotFoundException(String errorCode, String message) {
        super(builder(errorCode, ErrorCategory.NOT_FOUND, message));
    }
}
