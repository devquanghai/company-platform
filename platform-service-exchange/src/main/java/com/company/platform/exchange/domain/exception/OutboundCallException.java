package com.company.platform.exchange.domain.exception;

import com.company.platform.core.exception.PlatformIntegrationException;

public class OutboundCallException extends PlatformIntegrationException {

    private final String clientName;

    public OutboundCallException(String code, String clientName, String message, Throwable cause) {
        super(code, message, cause);
        this.clientName = clientName;
    }

    public String clientName() {
        return clientName;
    }
}
