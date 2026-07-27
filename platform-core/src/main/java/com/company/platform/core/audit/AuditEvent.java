package com.company.platform.core.audit;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.FieldDefaults;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Sự kiện audit chuẩn, mô tả người thực hiện, ngữ cảnh nghiệp vụ, request,
 * kết quả, thay đổi dữ liệu, thông tin giám sát, bảo mật và gian lận.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
@FieldDefaults(level = AccessLevel.PRIVATE)
public final class AuditEvent {

    OffsetDateTime timestamp;
    String serviceName;
    String environment;
    String traceId;
    String spanId;
    String correlationId;
    String businessContext;
    Audit audit;
    Device device;
    Api api;
    ApiResponse apiResponse;
    @ToString.Exclude
    DataChange dataChange;
    Monitor monitor;
    Security security;
    Fraud fraud;
    @Builder.Default
    @ToString.Exclude
    Map<String, Object> extra = Map.of();

    /**
     * Constructor tương thích với model audit cũ.
     *
     * @deprecated dùng builder và schema audit mới.
     */
    @Deprecated
    public AuditEvent(
        String actor,
        String action,
        String resource,
        String location,
        String mechanism,
        String requestId,
        String correlationId,
        OffsetDateTime timestamp,
        AuditOutcome outcome,
        String failureType,
        Map<String, Object> changes
    ) {
        this.timestamp = Objects.requireNonNull(timestamp, "timestamp must not be null");
        this.correlationId = normalize(correlationId);
        this.businessContext = normalize(resource);
        this.audit = Audit.builder()
            .isAnonymousUser(false)
            .username(requireText(actor, "actor"))
            .build();
        this.api = Api.builder()
            .action(requireText(action, "action"))
            .businessDescription(normalize(resource))
            .apiEndpoint(requireText(location, "location"))
            .httpMethod(requireText(mechanism, "mechanism"))
            .build();
        this.apiResponse = ApiResponse.builder()
            .status(toStatus(outcome))
            .errorMessage(normalize(failureType))
            .build();
        this.monitor = Monitor.builder()
            .requestId(normalize(requestId))
            .success(outcome == AuditOutcome.SUCCESS)
            .build();
        this.dataChange = fromChanges(changes);
        this.extra = Map.of();
    }

    /** @deprecated dùng {@code getAudit().getUsername()}. */
    @Deprecated
    public String getActor() {
        return audit == null ? null : audit.getUsername();
    }

    /** @deprecated dùng {@code getApi().getAction()}. */
    @Deprecated
    public String getAction() {
        return api == null ? null : api.getAction();
    }

    /** @deprecated dùng {@link #getBusinessContext()}. */
    @Deprecated
    public String getResource() {
        return businessContext;
    }

    /** @deprecated dùng {@code getApi().getApiEndpoint()}. */
    @Deprecated
    public String getLocation() {
        return api == null ? null : api.getApiEndpoint();
    }

    /** @deprecated dùng {@code getApi().getHttpMethod()}. */
    @Deprecated
    public String getMechanism() {
        return api == null ? null : api.getHttpMethod();
    }

    /** @deprecated dùng {@code getMonitor().getRequestId()}. */
    @Deprecated
    public String getRequestId() {
        return monitor == null ? null : monitor.getRequestId();
    }

    /** @deprecated dùng {@code getApiResponse().getStatus()}. */
    @Deprecated
    public AuditOutcome getOutcome() {
        if (apiResponse == null || apiResponse.getStatus() == null) {
            return null;
        }
        return apiResponse.getStatus() == AuditStatus.SUCCESS
            ? AuditOutcome.SUCCESS
            : AuditOutcome.FAILURE;
    }

    /** @deprecated dùng {@code getApiResponse().getErrorMessage()}. */
    @Deprecated
    public String getFailureType() {
        return apiResponse == null ? null : apiResponse.getErrorMessage();
    }

    /** @deprecated dùng {@link #getDataChange()}. */
    @Deprecated
    public Map<String, Object> getChanges() {
        if (dataChange == null || dataChange.getFieldChanges() == null) {
            return Map.of();
        }
        Map<String, Object> changes = new LinkedHashMap<>();
        dataChange.getFieldChanges().forEach(change ->
            changes.put(change.getField(), change.getNewValue())
        );
        return Map.copyOf(changes);
    }

    static DataChange fromChanges(Map<String, Object> changes) {
        if (changes == null || changes.isEmpty()) {
            return null;
        }
        List<FieldChange> fieldChanges = changes.entrySet().stream()
            .map(entry -> FieldChange.builder()
                .field(entry.getKey())
                .newValue(entry.getValue())
                .type(entry.getValue() == null ? null : entry.getValue().getClass().getName())
                .build())
            .toList();
        return DataChange.builder().fieldChanges(fieldChanges).build();
    }

    private static AuditStatus toStatus(AuditOutcome outcome) {
        if (outcome == null) {
            throw new NullPointerException("outcome must not be null");
        }
        return outcome == AuditOutcome.SUCCESS ? AuditStatus.SUCCESS : AuditStatus.FAILED;
    }

    private static String requireText(String value, String field) {
        String normalized = normalize(value);
        if (normalized == null) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class Audit {
        Boolean isAnonymousUser;
        String userId;
        String username;
        String email;
        String phone;
        @Builder.Default
        Set<String> roles = Set.of();
        @Builder.Default
        Set<String> permissions = Set.of();
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class Device {
        String deviceId;
        String deviceFingerprint;
        String ipAddress;
        String userAgent;
        String browser;
        String os;
        String country;
        String city;
        Double latitude;
        Double longitude;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class Api {
        String entity;
        String action;
        String businessDescription;
        String controllerClass;
        String methodName;
        String apiEndpoint;
        String httpMethod;
        @Builder.Default
        @ToString.Exclude
        Map<String, String> headers = Map.of();
        @Builder.Default
        @ToString.Exclude
        Map<String, String> queryParams = Map.of();
        String pathVariable;
        @ToString.Exclude
        String params;
        @ToString.Exclude
        Object requestBody;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class ApiResponse {
        AuditStatus status;
        String code;
        Integer httpStatus;
        @ToString.Exclude
        String responseBody;
        String errorMessage;
        Long responseSize;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class DataChange {
        String entityName;
        String entityId;
        @ToString.Exclude
        String oldValue;
        @ToString.Exclude
        String newValue;
        @Builder.Default
        List<FieldChange> fieldChanges = List.of();
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class FieldChange {
        String field;
        @ToString.Exclude
        Object oldValue;
        @ToString.Exclude
        Object newValue;
        String type;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class Monitor {
        String sessionId;
        String requestId;
        Long executionTimeMs;
        Long dbTimeMs;
        Long externalApiTimeMs;
        Boolean success;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class Security {
        String authType;
        String clientId;
        Boolean isInternalCall;
        String tokenId;
        String tokenIssuer;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class Fraud {
        Integer riskScore;
        @Builder.Default
        List<String> triggeredRules = List.of();
        Boolean isSuspicious;
    }

    public enum AuditStatus {
        SUCCESS,
        FAILED
    }
}
