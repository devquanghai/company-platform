package com.company.platform.exchange.api.http;

import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.Map;

@Getter
@Builder
public final class OutboundCallMetadata {
    private final String clientName;
    private final String requestId;
    private final String correlationId;
    private final String traceId;
    private final String spanId;
    private final int attemptCount;
    private final int retryCount;
    private final boolean fallbackUsed;
    private final OffsetDateTime timestamp;
    @Builder.Default
    private final Map<String, Object> attributes = Map.of();
}
