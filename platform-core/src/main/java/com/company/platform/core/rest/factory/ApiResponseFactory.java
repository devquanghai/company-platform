package com.company.platform.core.rest.factory;

import com.company.platform.core.rest.response.ApiError;
import com.company.platform.core.rest.response.ApiResponse;

import java.util.Map;
import java.util.Objects;

/** Creates success and failure envelopes with metadata from the active request. */
public final class ApiResponseFactory {

    private final ResponseMetadataFactory metadataFactory;

    public ApiResponseFactory(ResponseMetadataFactory metadataFactory) {
        this.metadataFactory = Objects.requireNonNull(
            metadataFactory,
            "metadataFactory must not be null"
        );
    }

    public <T> ApiResponse<T> success(T data) {
        return ApiResponse.success(data, metadataFactory.create());
    }

    public <T> ApiResponse<T> success(T data, Map<String, Object> attributes) {
        return ApiResponse.success(data, metadataFactory.create(attributes));
    }

    public ApiResponse<Void> success() {
        return ApiResponse.success(metadataFactory.create());
    }

    public <T> ApiResponse<T> failure(ApiError error) {
        return ApiResponse.failure(error, metadataFactory.create());
    }
}
