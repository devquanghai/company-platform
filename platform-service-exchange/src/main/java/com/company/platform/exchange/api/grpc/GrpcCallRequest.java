package com.company.platform.exchange.api.grpc;

import lombok.Builder;
import lombok.Getter;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

@Getter
public final class GrpcCallRequest {
    private static final Pattern SERVICE_NAME =
        Pattern.compile("[A-Za-z_][A-Za-z0-9_.]{0,127}");
    private static final Pattern METHOD_NAME =
        Pattern.compile("[A-Za-z_][A-Za-z0-9_]{0,63}");
    private static final Pattern AUDIT_ATTRIBUTE_KEY =
        Pattern.compile("[A-Za-z0-9][A-Za-z0-9_.-]{0,63}");
    private static final Pattern GRPC_METADATA_KEY =
        Pattern.compile("[0-9a-z][0-9a-z_.-]{0,63}");
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
        this.serviceName = validate("serviceName", serviceName, SERVICE_NAME);
        this.methodName = validate("methodName", methodName, METHOD_NAME);
        this.deadline = deadline;
        this.auditAttributes = validateAttributes(auditAttributes);
        this.requestMetadata = validateMetadata(requestMetadata);
        this.idempotent = idempotent;
        this.loggingEnabled = loggingEnabled;
        this.resilienceEnabled = resilienceEnabled;
        this.responseType = responseType == null ? Object.class : responseType;
    }

    private String validate(String field, String value, Pattern pattern) {
        Objects.requireNonNull(value, field);
        if (!pattern.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " has an invalid protobuf identifier");
        }
        return value;
    }

    private Map<String, Object> validateAttributes(Map<String, ?> source) {
        if (source == null) {
            return Map.of();
        }
        source.keySet().forEach(key -> validate(
            "audit attribute key", key, AUDIT_ATTRIBUTE_KEY));
        return Map.copyOf(source);
    }

    private Map<String, String> validateMetadata(Map<String, String> source) {
        if (source == null) {
            return Map.of();
        }
        source.forEach((key, value) -> {
            validate("gRPC metadata key", key, GRPC_METADATA_KEY);
            if (value == null || value.length() > 1024
                || value.chars().anyMatch(character -> character < 0x20 || character > 0x7e)) {
                throw new IllegalArgumentException("gRPC metadata value is invalid");
            }
        });
        return Map.copyOf(source);
    }
}
