package com.company.platform.exchange.resilience.fallback;

import java.util.Optional;

public interface OutboundFallbackRegistry {
    <T> Optional<OutboundFallbackHandler<T>> find(FallbackContext context, Class<T> responseType);
}
