package com.company.platform.core.config.web;

import com.company.platform.core.rest.factory.ResponseMetadataFactory;
import com.company.platform.core.rest.response.ApiResponse;
import com.company.platform.core.rest.response.ResponseMetadata;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.util.Objects;

/**
 * Enriches convenience {@link ApiResponse} instances with active HTTP and trace metadata.
 */
@ControllerAdvice
public final class PlatformApiResponseBodyAdvice implements ResponseBodyAdvice<Object> {

    private final ResponseMetadataFactory metadataFactory;

    public PlatformApiResponseBodyAdvice(ResponseMetadataFactory metadataFactory) {
        this.metadataFactory = Objects.requireNonNull(
            metadataFactory, "metadataFactory must not be null");
    }

    @Override
    public boolean supports(
        MethodParameter returnType,
        Class<? extends HttpMessageConverter<?>> converterType
    ) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(
        Object body,
        MethodParameter returnType,
        MediaType selectedContentType,
        Class<? extends HttpMessageConverter<?>> selectedConverterType,
        ServerHttpRequest request,
        ServerHttpResponse response
    ) {
        if (!(body instanceof ApiResponse<?> apiResponse)
            || hasRequestMetadata(apiResponse.getMetadata())) {
            return body;
        }

        ResponseMetadata metadata = metadataFactory.create(
            apiResponse.getMetadata().getAttributes());
        return apiResponse.isSuccess()
            ? ApiResponse.success(apiResponse.getData(), metadata)
            : ApiResponse.failure(apiResponse.getError(), metadata);
    }

    private static boolean hasRequestMetadata(ResponseMetadata metadata) {
        return metadata.getUrl() != null
            || metadata.getMethod() != null
            || metadata.getRequestId() != null
            || metadata.getCorrelationId() != null
            || metadata.hasTrace();
    }
}
