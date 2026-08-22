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
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;

public final class ProblemDetailAuthenticationEntryPoint implements AuthenticationEntryPoint {
    private final JsonMapperHelper jsonMapperHelper;
    private final ApiResponseFactory apiResponseFactory;

    public ProblemDetailAuthenticationEntryPoint(JsonMapperHelper jsonMapperHelper, ApiResponseFactory apiResponseFactory) {
        this.jsonMapperHelper = jsonMapperHelper;
        this.apiResponseFactory = apiResponseFactory;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authenticationException) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
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
