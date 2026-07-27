package com.company.platform.exchange.resilience.fallback;

import com.company.platform.exchange.domain.model.ExchangeProtocol;
import lombok.Builder;
import lombok.Getter;

import java.time.Duration;
import java.util.Map;

@Getter
@Builder
public final class FallbackContext {
    private final String clientName;
    private final ExchangeProtocol protocol;
    private final String operation;
    private final String transportMethod;
    private final Throwable finalException;
    private final String finalStatus;
    private final int retryCount;
    private final Duration duration;
    private final String circuitBreakerState;
    @Builder.Default
    private final Map<String, Object> requestMetadata = Map.of();
    private final String traceId;
    private final String requestId;
    private final Class<?> responseType;
}
