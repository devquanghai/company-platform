package com.company.platform.core.exception.handler;

import com.company.platform.core.exception.PlatformException;
import com.company.platform.core.configuration.properties.PlatformCoreExceptionProperties;
import com.company.platform.core.exception.code.CommonCode;
import com.company.platform.core.exception.code.FileCode;
import com.company.platform.core.exception.code.ValidationCode;
import com.company.platform.core.exception.error.ErrorCategory;
import com.company.platform.core.i18n.I18nService;
import com.company.platform.core.i18n.I18nKey;
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
import org.springframework.validation.FieldError;
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
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;
import java.util.Objects;
import java.util.ArrayList;

@Slf4j
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE - 100)
public final class PlatformExceptionHandler {

    private final ResponseMetadataFactory metadataFactory;
    private final PlatformCoreExceptionProperties properties;
    private final I18nService i18n;

    public PlatformExceptionHandler(
        ResponseMetadataFactory metadataFactory,
        PlatformCoreExceptionProperties properties,
        I18nService i18n
    ) {
        this.metadataFactory = Objects.requireNonNull(metadataFactory, "metadataFactory must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.i18n = Objects.requireNonNull(i18n, "i18n must not be null");
    }

    @ExceptionHandler(PlatformException.class)
    public ResponseEntity<ApiResponse<Void>> handlePlatformException(
        PlatformException exception,
        HttpServletRequest request
    ) {
        ApiError error = new ApiError(
            exception.errorCode(),
            i18n.getOrDefault(exception.errorCode(), exception.getMessage()),
            exception.category(),
            List.of()
        );
        return response(exception.category().getHttpStatus(), error, request);
    }

    @ExceptionHandler({BindException.class, MethodArgumentNotValidException.class})
    public ResponseEntity<ApiResponse<Void>> handleBindException(
        BindException exception,
        HttpServletRequest request
    ) {
        return validationResponse(exception.getBindingResult().getFieldErrors(), request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(
        ConstraintViolationException exception,
        HttpServletRequest request
    ) {
        List<ErrorDetail> details = exception.getConstraintViolations().stream()
            .map(violation -> new ErrorDetail(
                violation.getPropertyPath().toString(),
                ValidationCode.FIELD_INVALID.getKey(),
                violation.getMessage(),
                properties.isIncludeRejectedValue() ? violation.getInvalidValue() : null,
                null
            ))
            .toList();
        return validationResponse(details, request);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodValidation(
        HandlerMethodValidationException exception,
        HttpServletRequest request
    ) {
        List<ErrorDetail> details = new ArrayList<>();
        exception.getParameterValidationResults().forEach(result -> {
            String field = result.getMethodParameter().getParameterName();
            result.getResolvableErrors().forEach(error -> details.add(new ErrorDetail(
                field == null ? "argument" : field,
                firstCode(error.getCodes()),
                error.getDefaultMessage() == null ? "Invalid value" : error.getDefaultMessage(),
                properties.isIncludeRejectedValue() ? result.getArgument() : null,
                null
            )));
        });
        return validationResponse(details, request);
    }

    @ExceptionHandler({
        HttpMessageNotReadableException.class,
        HttpMessageConversionException.class,
        MissingServletRequestParameterException.class,
        ServletRequestBindingException.class,
        MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleInvalidRequest(
        Exception exception,
        HttpServletRequest request
    ) {
        log.debug("Invalid request: {}", exception.getMessage());
        ApiError error = ApiError.of(
            CommonCode.INVALID_REQUEST.getKey(),
            i18n.get(CommonCode.INVALID_REQUEST),
            ErrorCategory.VALIDATION
        );
        return response(HttpStatus.BAD_REQUEST, error, request);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUploadSizeExceeded(
        MaxUploadSizeExceededException exception,
        HttpServletRequest request
    ) {
        log.debug("Upload exceeds configured size limit: {}", exception.getMaxUploadSize());
        return response(
            HttpStatus.CONTENT_TOO_LARGE,
            error(FileCode.SIZE_EXCEEDED, ErrorCategory.VALIDATION, exception.getMaxUploadSize()),
            request
        );
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleResourceNotFound(
        NoResourceFoundException exception,
        HttpServletRequest request
    ) {
        log.debug("Resource not found: {}", exception.getResourcePath());
        return response(
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
        return response(
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
        return response(
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
        log.debug("No acceptable response media type", exception);
        return response(
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
        log.warn("Asynchronous request timed out", exception);
        return response(
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
        ErrorCategory category = status.is4xxClientError()
            ? ErrorCategory.VALIDATION
            : ErrorCategory.INTERNAL;
        return response(status, error(CommonCode.REQUEST_FAILED, category), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpectedException(
        Exception exception,
        HttpServletRequest request
    ) {
        log.error("Unhandled platform request failure", exception);
        ApiError error = ApiError.of(
            CommonCode.INTERNAL_SERVER_ERROR.getKey(),
            i18n.get(CommonCode.INTERNAL_SERVER_ERROR),
            ErrorCategory.INTERNAL
        );
        return response(HttpStatus.INTERNAL_SERVER_ERROR, error, request);
    }

    private ResponseEntity<ApiResponse<Void>> validationResponse(
        List<FieldError> fieldErrors,
        HttpServletRequest request
    ) {
        List<ErrorDetail> details = fieldErrors.stream()
            .map(this::toDetail)
            .toList();
        return validationResponse(details, request);
    }

    private ResponseEntity<ApiResponse<Void>> validationResponse(
        Iterable<ErrorDetail> errorDetails,
        HttpServletRequest request
    ) {
        List<ErrorDetail> details = new ArrayList<>();
        errorDetails.forEach(details::add);
        ApiError error = new ApiError(
            ValidationCode.FAILED.getKey(),
            i18n.get(ValidationCode.FAILED),
            ErrorCategory.VALIDATION,
            details
        );
        return response(HttpStatus.BAD_REQUEST, error, request);
    }

    private static String firstCode(String[] codes) {
        return codes == null || codes.length == 0
            ? ValidationCode.FIELD_INVALID.getKey()
            : codes[0];
    }

    private ErrorDetail toDetail(FieldError fieldError) {
        String code = org.springframework.util.StringUtils.hasText(fieldError.getCode())
            ? fieldError.getCode()
            : ValidationCode.FIELD_INVALID.getKey();
        String fallback = org.springframework.util.StringUtils.hasText(fieldError.getDefaultMessage())
            ? fieldError.getDefaultMessage()
            : "Invalid value";
        String message = i18n.getOrDefault(code, fallback, fieldError.getField());
        Object rejectedValue = properties.isIncludeRejectedValue()
            ? fieldError.getRejectedValue()
            : null;
        return new ErrorDetail(fieldError.getField(), code, message, rejectedValue, null);
    }

    private ApiError error(I18nKey code, ErrorCategory category, Object... arguments) {
        return ApiError.of(code.getKey(), i18n.get(code, arguments), category);
    }

    private ResponseEntity<ApiResponse<Void>> response(
        HttpStatusCode status,
        ApiError error,
        HttpServletRequest request
    ) {
        String url = request == null ? null : request.getRequestURI();
        String method = request == null ? null : request.getMethod();
        ApiResponse<Void> body = ApiResponse.failure(error, metadataFactory.create(url, method));
        return ResponseEntity.status(status).body(body);
    }
}
