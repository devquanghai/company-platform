package com.company.platform.exchange.domain.exception;

public final class ClientNotFoundException extends OutboundCallException {
    public ClientNotFoundException(String clientName) {
        super("EXCHANGE.CLIENT_NOT_FOUND", clientName,
            "Outbound client is not configured: " + clientName, null);
    }
}
