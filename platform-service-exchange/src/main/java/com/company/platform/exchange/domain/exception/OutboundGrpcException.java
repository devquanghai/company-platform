package com.company.platform.exchange.domain.exception;

import io.grpc.Status;
import lombok.Getter;

import java.time.Duration;
import java.util.Map;

@Getter
public final class OutboundGrpcException extends OutboundCallException {

    private final String service;
    private final String method;
    private final Status.Code status;
    private final Map<String, String> trailers;
    private final int retryCount;
    private final Duration deadline;
    private final boolean retryable;

    public OutboundGrpcException(
        String clientName, String service, String method, Status.Code status,
        Map<String, String> trailers, int retryCount, Duration deadline,
        boolean retryable, Throwable cause
    ) {
        super("EXCHANGE.GRPC_FAILED", clientName,
            "gRPC outbound call failed: client=" + clientName + ", service=" + service
                + ", method=" + method + ", status=" + status, cause);
        this.service = service;
        this.method = method;
        this.status = status;
        this.trailers = trailers == null ? Map.of() : Map.copyOf(trailers);
        this.retryCount = retryCount;
        this.deadline = deadline;
        this.retryable = retryable;
    }
}
