package com.company.platform.exchange.domain.exception;

public final class OutboundCircuitOpenException extends OutboundCallException {
    public OutboundCircuitOpenException(String clientName, Throwable cause) {
        super("EXCHANGE.CIRCUIT_OPEN", clientName,
            "Outbound circuit is open: client=" + clientName, cause);
    }
}
