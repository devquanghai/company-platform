package com.company.platform.core.rest.response;

import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.FieldDefaults;

import java.util.Map;

@Getter
@ToString
@EqualsAndHashCode
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public final class ErrorDetail {
    String field;
    String code;
    String message;
    @ToString.Exclude
    Object rejectedValue;
    Map<String, Object> metadata;

    public ErrorDetail(String field, String code, String message, Object rejectedValue, Map<String, Object> metadata) {
        this.field = field;
        this.code = code;
        this.message = message;
        this.rejectedValue = rejectedValue;
        this.metadata = Map.copyOf(metadata == null ? Map.of() : metadata);
    }

    public static ErrorDetail of(String field, String code, String message) {
        return new ErrorDetail(field, code, message, null, Map.of());
    }

    public static ErrorDetail of(String field, String code, String message, Object rejectedValue) {
        return new ErrorDetail(field, code, message, rejectedValue, Map.of());
    }
}
