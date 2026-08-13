package com.company.platform.cache.internal.resilience;

final class CacheResilienceRejectedException extends RuntimeException {
    enum Reason {
        CIRCUIT_OPEN,
        BULKHEAD_FULL
    }

    private final Reason reason;

    CacheResilienceRejectedException(
        String message,
        Throwable cause,
        Reason reason
    ) {
        super(message, cause);
        this.reason = reason;
    }

    Reason getReason() {
        return reason;
    }
}
