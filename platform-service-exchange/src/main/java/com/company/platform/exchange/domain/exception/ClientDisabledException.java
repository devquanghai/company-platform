package com.company.platform.exchange.domain.exception;

public final class ClientDisabledException extends OutboundCallException {
    public ClientDisabledException(String clientName) {
        super("EXCHANGE.CLIENT_DISABLED", clientName,
            "Outbound client is disabled: " + clientName, null);
    }
}
