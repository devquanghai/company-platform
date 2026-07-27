package com.company.platform.core.exception;

import com.company.platform.core.exception.error.ErrorCategory;

public class PlatformBusinessException extends PlatformException {

    public PlatformBusinessException(String errorCode, String message) {
        super(builder(errorCode, ErrorCategory.BUSINESS, message));
    }
}
