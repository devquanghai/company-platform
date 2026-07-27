package com.company.platform.exchange.domain.exception;

public final class InvalidClientConfigurationException extends OutboundCallException {
    public InvalidClientConfigurationException(String clientName, String detail) {
        super("EXCHANGE.INVALID_CONFIGURATION", clientName,
            "Invalid outbound client configuration [" + clientName + "]: " + detail, null);
    }
}
