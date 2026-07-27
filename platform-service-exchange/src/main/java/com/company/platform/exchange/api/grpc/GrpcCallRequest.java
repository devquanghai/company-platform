package com.company.platform.exchange.api.grpc;

import lombok.Builder;
import lombok.Getter;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;

@Getter
public final class GrpcCallRequest {
    private final String clientName;
    private final String serviceName;
    private final String methodName;
    private final Duration deadline;
    private final Map<String, Object> auditAttributes;
    private final Map<String, String> requestMetadata;
    private final boolean idempotent;
    private final Boolean loggingEnabled;
    private final Boolean resilienceEnabled;
    private final Class<?> responseType;

    @Builder
    public GrpcCallRequest(
        String clientName, String serviceName, String methodName,
        Duration deadline, Map<String, ?> auditAttributes,
        Map<String, String> requestMetadata, boolean idempotent,
        Boolean loggingEnabled, Boolean resilienceEnabled, Class<?> responseType
    ) {
        this.clientName = Objects.requireNonNull(clientName, "clientName");
        this.serviceName = Objects.requireNonNull(serviceName, "serviceName");
        this.methodName = Objects.requireNonNull(methodName, "methodName");
        this.deadline = deadline;
        this.auditAttributes = Map.copyOf(auditAttributes == null ? Map.of() : auditAttributes);
        this.requestMetadata = Map.copyOf(requestMetadata == null ? Map.of() : requestMetadata);
        this.idempotent = idempotent;
        this.loggingEnabled = loggingEnabled;
        this.resilienceEnabled = resilienceEnabled;
        this.responseType = responseType == null ? Object.class : responseType;
    }
}
