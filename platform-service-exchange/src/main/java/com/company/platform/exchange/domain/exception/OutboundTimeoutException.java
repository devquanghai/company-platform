package com.company.platform.exchange.domain.exception;

public final class OutboundTimeoutException extends OutboundCallException {
    public OutboundTimeoutException(String clientName, Throwable cause) {
        super("EXCHANGE.TIMEOUT", clientName,
            "Outbound call timed out: client=" + clientName, cause);
    }
}
