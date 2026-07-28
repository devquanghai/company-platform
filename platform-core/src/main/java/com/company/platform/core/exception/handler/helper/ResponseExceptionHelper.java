package com.company.platform.core.exception.handler.helper;

import com.company.platform.core.exception.error.ErrorCategory;
import com.company.platform.core.i18n.I18nService;
import com.company.platform.core.rest.factory.ResponseMetadataFactory;
import com.company.platform.core.rest.response.ApiError;
import com.company.platform.core.rest.response.ApiResponse;
import com.company.platform.core.rest.response.ErrorDetail;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;

import java.util.List;

@RequiredArgsConstructor
public final class ResponseExceptionHelper {

    private final ResponseMetadataFactory metadataFactory;

    private final I18nService i18n;

    public ResponseEntity<ApiResponse<Void>> validation(
        String code,
        String message,
        List<ErrorDetail> details,
        HttpServletRequest request
    ) {

        ApiError error = new ApiError(
            code,
            message,
            ErrorCategory.VALIDATION,
            details
        );

        return response(HttpStatus.BAD_REQUEST, error, request);
    }

    public ResponseEntity<ApiResponse<Void>> response(
        HttpStatusCode status,
        ApiError error,
        HttpServletRequest request
    ) {

        String url =
            request == null
                ? null
                : request.getRequestURI();

        String method =
            request == null
                ? null
                : request.getMethod();

        return ResponseEntity
            .status(status)
            .body(
                ApiResponse.failure(
                    error,
                    metadataFactory.create(
                        url,
                        method
                    )
                )
            );
    }
}
