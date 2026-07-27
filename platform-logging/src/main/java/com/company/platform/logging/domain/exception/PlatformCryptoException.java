package com.company.platform.logging.domain.exception;

import com.company.platform.core.exception.PlatformInfrastructureException;

public final class PlatformCryptoException extends PlatformInfrastructureException {
    public PlatformCryptoException(String code, String detail) {
        super(code, detail, null);
    }

    public PlatformCryptoException(String code, String detail, Throwable cause) {
        super(code, detail, cause);
    }
}
