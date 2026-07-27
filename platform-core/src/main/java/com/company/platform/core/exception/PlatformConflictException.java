package com.company.platform.core.exception;

import com.company.platform.core.exception.error.ErrorCategory;

public class PlatformConflictException extends PlatformException {
    public PlatformConflictException(String errorCode, String message) {
        super(builder(errorCode, ErrorCategory.CONFLICT, message));
    }
}
