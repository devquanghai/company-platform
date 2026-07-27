package com.company.platform.exchange.domain.exception;

public final class OutboundFallbackException extends OutboundCallException {
    public OutboundFallbackException(String clientName, Throwable cause) {
        super("EXCHANGE.FALLBACK_FAILED", clientName,
            "Outbound fallback failed: client=" + clientName, cause);
    }
}
