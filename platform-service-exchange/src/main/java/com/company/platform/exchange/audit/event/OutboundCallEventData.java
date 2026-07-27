package com.company.platform.exchange.audit.event;

import com.company.platform.exchange.domain.model.ExchangeProtocol;
import lombok.Builder;
import lombok.Getter;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Getter
@Builder
public final class OutboundCallEventData {
    @Builder.Default
    private final String eventId = UUID.randomUUID().toString();
    private final String clientName;
    private final ExchangeProtocol protocol;
    private final String operation;
    private final String httpMethod;
    private final String grpcService;
    private final String grpcMethod;
    private final String target;
    private final OffsetDateTime startedAt;
    private final OffsetDateTime completedAt;
    private final Duration duration;
    private final boolean success;
    private final Integer httpStatus;
    private final String grpcStatus;
    private final int attemptCount;
    private final int retryCount;
    private final boolean fallbackUsed;
    private final String circuitBreakerState;
    private final boolean rateLimited;
    private final boolean timedOut;
    private final String requestId;
    private final String traceId;
    private final String spanId;
    private final String sourceApplication;
    private final String errorType;
    private final String errorCode;
    private final String errorMessage;
    private final String requestPayloadHash;
    private final String responsePayloadHash;
    @Builder.Default
    private final Map<String, Object> customAttributes = Map.of();
}
