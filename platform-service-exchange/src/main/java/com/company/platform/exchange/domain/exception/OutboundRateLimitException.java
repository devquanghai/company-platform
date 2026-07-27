package com.company.platform.exchange.domain.exception;

public final class OutboundRateLimitException extends OutboundCallException {
    public OutboundRateLimitException(String clientName, Throwable cause) {
        super("EXCHANGE.RATE_LIMITED", clientName,
            "Outbound call was rate limited: client=" + clientName, cause);
    }
}
