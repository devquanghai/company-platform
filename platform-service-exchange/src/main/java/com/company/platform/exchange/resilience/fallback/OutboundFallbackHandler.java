package com.company.platform.exchange.resilience.fallback;

public interface OutboundFallbackHandler<T> {
    Class<T> responseType();
    boolean supports(FallbackContext context);
    T fallback(FallbackContext context);
}
