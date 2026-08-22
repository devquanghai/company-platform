package com.company.platform.security.web.internal;

import com.company.platform.core.exception.error.ErrorCategory;
import com.company.platform.core.json.JsonMapperHelper;
import com.company.platform.core.rest.factory.ApiResponseFactory;
import com.company.platform.core.rest.response.ApiError;
import com.company.platform.core.rest.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;

public final class ProblemDetailAccessDeniedHandler implements AccessDeniedHandler {
    private final ApiResponseFactory apiResponseFactory;
    private final JsonMapperHelper jsonMapperHelper;

    public ProblemDetailAccessDeniedHandler(ApiResponseFactory apiResponseFactory, JsonMapperHelper jsonMapperHelper) {
        this.apiResponseFactory = apiResponseFactory;
        this.jsonMapperHelper = jsonMapperHelper;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        ApiError apiError = ApiError.of(
            "Authentication required",
            "The request requires authentication, but no valid credentials were provided.",
            ErrorCategory.AUTHENTICATION
        );
        ApiResponse<?> apiResponse = apiResponseFactory.failure(apiError);
        response.getWriter().write(jsonMapperHelper.toJson(apiResponse));
    }
}
