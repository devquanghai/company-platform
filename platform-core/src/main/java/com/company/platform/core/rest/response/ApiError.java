package com.company.platform.core.rest.response;

import com.company.platform.core.exception.error.ErrorCategory;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.FieldDefaults;

import java.util.List;
import java.util.Objects;

@Getter
@ToString
@EqualsAndHashCode
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public final class ApiError {
    String code;
    String message;
    ErrorCategory category;
    List<ErrorDetail> details;

    public ApiError(String code, String message, ErrorCategory category, List<ErrorDetail> details) {
        this.code = requireNotBlank(code, "code");
        this.message = requireNotBlank(message, "message");
        this.category = Objects.requireNonNull(category, "category must not be null");
        this.details = List.copyOf(details == null ? List.of() : details);
    }

    public static ApiError of(String code, String message, ErrorCategory category) {
        return new ApiError(code, message, category, List.of());
    }

    public boolean hasDetails() {
        return !details.isEmpty();
    }

    private static String requireNotBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
