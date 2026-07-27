package com.company.platform.core.rest.response;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.FieldDefaults;

import java.util.Objects;

@Getter
@ToString
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public final class ApiResponse<T> {
    boolean success;
    T data;
    ApiError error;
    ResponseMetadata metadata;

    private ApiResponse(boolean success, T data, ApiError error, ResponseMetadata metadata) {
        this.metadata = Objects.requireNonNull(metadata, "metadata must not be null");
        if (success && error != null) {
            throw new IllegalArgumentException("success response must not contain error");
        }
        if (!success && data != null) {
            throw new IllegalArgumentException("failure response must not contain data");
        }
        if (!success && error == null) {
            throw new IllegalArgumentException("failure response must contain error");
        }
        this.success = success;
        this.data = data;
        this.error = error;
    }

    public static <T> ApiResponse<T> success(T data, ResponseMetadata metadata) {
        return new ApiResponse<>(true, data, null, metadata);
    }

    public static <T> ApiResponse<T> success(T data) {
        return success(data, ResponseMetadata.empty());
    }

    public static ApiResponse<Void> success(ResponseMetadata metadata) {
        return new ApiResponse<>(true, null, null, metadata);
    }

    public static <T> ApiResponse<T> failure(ApiError error, ResponseMetadata metadata) {
        return new ApiResponse<>(false, null, error, metadata);
    }

    public static <T> ApiResponse<T> failure(ApiError error) {
        return failure(error, ResponseMetadata.empty());
    }
}
