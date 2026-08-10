package com.company.platform.core.exception.internal.adapter;

import com.company.platform.core.configuration.properties.PlatformCoreExceptionProperties;
import com.company.platform.core.exception.PlatformException;
import com.company.platform.core.exception.code.CommonCode;
import com.company.platform.core.exception.code.FileCode;
import com.company.platform.core.exception.code.ValidationCode;
import com.company.platform.core.exception.error.ErrorCategory;
import com.company.platform.core.exception.internal.adapter.helper.HandlerExceptionHelper;
import com.company.platform.core.i18n.I18nKey;
import com.company.platform.core.i18n.I18nService;
import com.company.platform.core.rest.factory.ResponseMetadataFactory;
import com.company.platform.core.rest.response.ApiError;
import com.company.platform.core.rest.response.ApiResponse;
import com.company.platform.core.rest.response.ErrorDetail;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;
import java.util.Objects;

/** Stable, localized and non-sensitive REST exception mapping. */
@Slf4j
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE - 100)
public final class PlatformExceptionHandler {

    private final I18nService i18n;
    private final HandlerExceptionHelper helpers;

    public PlatformExceptionHandler(
        ResponseMetadataFactory metadataFactory,
        PlatformCoreExceptionProperties properties,
        I18nService i18n
    ) {
        this.i18n = Objects.requireNonNull(i18n, "i18n must not be null");
        this.helpers = new HandlerExceptionHelper(
            Objects.requireNonNull(properties, "properties must not be null"),
            this.i18n,
            Objects.requireNonNull(metadataFactory, "metadataFactory must not be null")
        );
    }

    @ExceptionHandler(PlatformException.class)
    public ResponseEntity<ApiResponse<Void>> handlePlatformException(
        PlatformException exception,
        HttpServletRequest request
    ) {
        Object[] arguments = exception.parameters().values().toArray();
        ApiError error = new ApiError(
            exception.errorCode(),
            i18n.getOrDefault(exception.errorCode(), exception.getMessage(), arguments),
            exception.category(),
            List.of()
        );
        logKnownFailure(exception.category(), exception.errorCode(), exception);
        return helpers.getResponse().response(
            exception.category().getHttpStatus(), error, request);
    }

    @ExceptionHandler({BindException.class, MethodArgumentNotValidException.class})
    public ResponseEntity<ApiResponse<Void>> handleBindException(
        BindException exception,
        HttpServletRequest request
    ) {
        log.debug("Request validation failed: {}", requestDescription(request));
        List<ErrorDetail> details = exception.getBindingResult().getFieldErrors().stream()
            .map(helpers.getValidation()::toDetail)
            .toList();
        return validation(details, request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(
        ConstraintViolationException exception,
        HttpServletRequest request
    ) {
        log.debug("Constraint violation failed: {}", requestDescription(request));
        return validation(
            exception.getConstraintViolations().stream()
                .map(helpers.getValidation()::toDetail)
                .toList(),
            request
        );
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodValidation(
        HandlerMethodValidationException exception,
        HttpServletRequest request
    ) {
        log.debug("Method validation failed: {}", requestDescription(request));
        return validation(helpers.getValidation().toDetails(exception), request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleHttpMessageNotReadable(
        HttpMessageNotReadableException exception,
        HttpServletRequest request
    ) {
        log.debug("Request body could not be parsed: {}", requestDescription(request));
        return validation(helpers.getJson().extractJsonErrorDetails(exception), request);
    }

    @ExceptionHandler(HttpMessageConversionException.class)
    public ResponseEntity<ApiResponse<Void>> handleHttpMessageConversion(
        HttpMessageConversionException exception,
        HttpServletRequest request
    ) {
        log.warn("Request conversion configuration failed: {}", requestDescription(request));
        return validation(helpers.getJson().extractJsonErrorDetails(exception), request);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParameter(
        MissingServletRequestParameterException exception,
        HttpServletRequest request
    ) {
        log.debug("Missing required request parameter: {}", exception.getParameterName());
        return validation(List.of(helpers.getValidation().missingParameter(exception)), request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(
        MethodArgumentTypeMismatchException exception,
        HttpServletRequest request
    ) {
        return validation(List.of(helpers.getValidation().typeMismatch(exception)), request);
    }

    @ExceptionHandler(ServletRequestBindingException.class)
    public ResponseEntity<ApiResponse<Void>> handleServletRequestBinding(
        ServletRequestBindingException exception,
        HttpServletRequest request
    ) {
        return validation(List.of(helpers.getValidation().requestBinding()), request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidRequest(
        RuntimeException exception,
        HttpServletRequest request
    ) {
        log.debug("Invalid request state: {}", requestDescription(request));
        return helpers.getResponse().response(
            HttpStatus.BAD_REQUEST,
            error(CommonCode.INVALID_REQUEST, ErrorCategory.VALIDATION),
            request
        );
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUploadSizeExceeded(
        MaxUploadSizeExceededException exception,
        HttpServletRequest request
    ) {
        log.debug("Upload exceeds configured size limit: {}", exception.getMaxUploadSize());
        return helpers.getResponse().response(
            HttpStatus.CONTENT_TOO_LARGE,
            error(FileCode.SIZE_EXCEEDED, ErrorCategory.VALIDATION,
                exception.getMaxUploadSize()),
            request
        );
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleResourceNotFound(
        NoResourceFoundException exception,
        HttpServletRequest request
    ) {
        log.debug("Resource not found: {}", exception.getResourcePath());
        return helpers.getResponse().response(
            HttpStatus.NOT_FOUND,
            error(CommonCode.URL_NOT_FOUND, ErrorCategory.NOT_FOUND),
            request
        );
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotAllowed(
        HttpRequestMethodNotSupportedException exception,
        HttpServletRequest request
    ) {
        log.debug("HTTP method not supported: {}", exception.getMethod());
        return helpers.getResponse().response(
            HttpStatus.METHOD_NOT_ALLOWED,
            error(CommonCode.METHOD_NOT_ALLOWED, ErrorCategory.VALIDATION),
            request
        );
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnsupportedMediaType(
        HttpMediaTypeNotSupportedException exception,
        HttpServletRequest request
    ) {
        log.debug("Media type not supported: {}", exception.getContentType());
        return helpers.getResponse().response(
            HttpStatus.UNSUPPORTED_MEDIA_TYPE,
            error(CommonCode.UNSUPPORTED_MEDIA_TYPE, ErrorCategory.VALIDATION),
            request
        );
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotAcceptable(
        HttpMediaTypeNotAcceptableException exception,
        HttpServletRequest request
    ) {
        log.debug("No acceptable response media type: {}", requestDescription(request));
        return helpers.getResponse().response(
            HttpStatus.NOT_ACCEPTABLE,
            error(CommonCode.NOT_ACCEPTABLE, ErrorCategory.VALIDATION),
            request
        );
    }

    @ExceptionHandler(AsyncRequestTimeoutException.class)
    public ResponseEntity<ApiResponse<Void>> handleRequestTimeout(
        AsyncRequestTimeoutException exception,
        HttpServletRequest request
    ) {
        log.warn("Asynchronous request timed out: {}", requestDescription(request));
        return helpers.getResponse().response(
            HttpStatus.SERVICE_UNAVAILABLE,
            error(CommonCode.REQUEST_TIMEOUT, ErrorCategory.TIMEOUT),
            request
        );
    }

    @ExceptionHandler({ResponseStatusException.class, ErrorResponseException.class})
    public ResponseEntity<ApiResponse<Void>> handleErrorResponse(
        ErrorResponseException exception,
        HttpServletRequest request
    ) {
        HttpStatusCode status = exception.getStatusCode();
        return helpers.getResponse().response(
            status,
            error(CommonCode.REQUEST_FAILED, category(status)),
            request
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpectedException(
        Exception exception,
        HttpServletRequest request
    ) {
        log.error("Unhandled platform request failure: {}", requestDescription(request), exception);
        return helpers.getResponse().response(
            HttpStatus.INTERNAL_SERVER_ERROR,
            error(CommonCode.INTERNAL_SERVER_ERROR, ErrorCategory.INTERNAL),
            request
        );
    }

    private ResponseEntity<ApiResponse<Void>> validation(
        List<ErrorDetail> details,
        HttpServletRequest request
    ) {
        return helpers.getResponse().validation(
            ValidationCode.FAILED.getKey(),
            i18n.get(ValidationCode.FAILED),
            details,
            request
        );
    }

    private ApiError error(I18nKey code, ErrorCategory category, Object... arguments) {
        return ApiError.of(code.getKey(), i18n.get(code, arguments), category);
    }

    private static ErrorCategory category(HttpStatusCode status) {
        if (status.value() == HttpStatus.UNAUTHORIZED.value()) {
            return ErrorCategory.AUTHENTICATION;
        }
        if (status.value() == HttpStatus.FORBIDDEN.value()) {
            return ErrorCategory.AUTHORIZATION;
        }
        if (status.value() == HttpStatus.NOT_FOUND.value()) {
            return ErrorCategory.NOT_FOUND;
        }
        if (status.value() == HttpStatus.CONFLICT.value()) {
            return ErrorCategory.CONFLICT;
        }
        if (status.value() == HttpStatus.TOO_MANY_REQUESTS.value()) {
            return ErrorCategory.RATE_LIMIT;
        }
        return status.is4xxClientError()
            ? ErrorCategory.VALIDATION
            : ErrorCategory.INTERNAL;
    }

    private static void logKnownFailure(
        ErrorCategory category,
        String errorCode,
        PlatformException exception
    ) {
        if (category.getHttpStatus().is5xxServerError()) {
            log.error("Platform failure code={} category={}", errorCode, category, exception);
        } else {
            log.debug("Platform request rejected code={} category={}", errorCode, category);
        }
    }

    private static String requestDescription(HttpServletRequest request) {
        return request == null
            ? "request=unavailable"
            : "method=" + request.getMethod() + ", path=" + request.getRequestURI();
    }
}
