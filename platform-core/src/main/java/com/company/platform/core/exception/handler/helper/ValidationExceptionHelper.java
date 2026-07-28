package com.company.platform.core.exception.handler.helper;

import com.company.platform.core.configuration.properties.PlatformCoreExceptionProperties;
import com.company.platform.core.exception.code.ValidationCode;
import com.company.platform.core.i18n.I18nService;
import com.company.platform.core.rest.response.ErrorDetail;
import jakarta.validation.ConstraintViolation;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RequiredArgsConstructor
public final class ValidationExceptionHelper {

    private static final Map<String, ValidationCode> STANDARD_CONSTRAINT_CODES = Map.of(
        "NotBlank", ValidationCode.FIELD_REQUIRED,
        "NotEmpty", ValidationCode.FIELD_REQUIRED,
        "NotNull", ValidationCode.FIELD_REQUIRED,
        "Email", ValidationCode.FIELD_EMAIL,
        "Pattern", ValidationCode.FIELD_PATTERN,
        "Positive", ValidationCode.FIELD_POSITIVE,
        "PositiveOrZero", ValidationCode.FIELD_POSITIVE_OR_ZERO,
        "Negative", ValidationCode.FIELD_NEGATIVE,
        "NegativeOrZero", ValidationCode.FIELD_NEGATIVE_OR_ZERO
    );
    private static final Pattern STANDARD_CONSTRAINT_PATTERN = Pattern.compile(
        "(?:^|\\.)(NotBlank|NotEmpty|NotNull|Email|Pattern|Positive|"
            + "PositiveOrZero|Negative|NegativeOrZero)(?:\\.|$)"
    );

    private final PlatformCoreExceptionProperties properties;

    private final I18nService i18n;

    public ErrorDetail toDetail(
        FieldError fieldError
    ) {
        String code = stableCode(
            fieldError.getCode(),
            ValidationCode.FIELD_INVALID.getKey()
        );
        String fallbackMessage =
            fieldError.getDefaultMessage() == null
                || fieldError.getDefaultMessage().isBlank()
                ? "Invalid value."
                : fieldError.getDefaultMessage();

        return new ErrorDetail(
            fieldError.getField(),
            code,
            i18n.getOrDefault(
                code,
                fallbackMessage,
                fieldError.getField()
            ),
            properties.isIncludeRejectedValue()
                ? fieldError.getRejectedValue()
                : null,
            null
        );
    }

    public ErrorDetail toDetail(ConstraintViolation<?> violation) {
        String field = violation.getPropertyPath().toString();
        String code = stableCode(
            violation.getMessageTemplate(),
            ValidationCode.FIELD_INVALID.getKey()
        );
        String fallbackMessage =
            violation.getMessage() == null || violation.getMessage().isBlank()
                ? "Invalid value."
                : violation.getMessage();
        return new ErrorDetail(
            field,
            code,
            i18n.getOrDefault(
                code,
                fallbackMessage,
                field
            ),
            properties.isIncludeRejectedValue() ? violation.getInvalidValue() : null,
            null
        );
    }

    public List<ErrorDetail> toDetails(HandlerMethodValidationException exception) {
        List<ErrorDetail> details = new ArrayList<>();
        exception.getParameterValidationResults().forEach(result -> {
            String parameterName = result.getMethodParameter().getParameterName();
            String field = parameterName == null ? "argument" : parameterName;
            result.getResolvableErrors().forEach(error ->
                details.add(toDetail(field, result.getArgument(), error)));
        });
        return List.copyOf(details);
    }

    public ErrorDetail missingParameter(
        MissingServletRequestParameterException exception
    ) {

        return new ErrorDetail(
            exception.getParameterName(),
            ValidationCode.FIELD_REQUIRED.getKey(),
            i18n.getOrDefault(
                "validation.required",
                "Field is required.",
                exception.getParameterName()
            ),
            null,
            null
        );
    }

    public ErrorDetail requestBinding() {
        return ErrorDetail.of(
            "request",
            ValidationCode.FIELD_INVALID.getKey(),
            i18n.getOrDefault(
                ValidationCode.FIELD_INVALID.getKey(),
                "Request binding is invalid.",
                "request"
            )
        );
    }

    public ErrorDetail typeMismatch(
        MethodArgumentTypeMismatchException exception
    ) {

        String expectedType =
            exception.getRequiredType() == null
                ? "unknown"
                : exception.getRequiredType().getSimpleName();

        return new ErrorDetail(
            exception.getName(),
            ValidationCode.FIELD_INVALID.getKey(),
            i18n.getOrDefault(
                "validation.invalid.type",
                "Invalid value type.",
                exception.getName(),
                expectedType
            ),
            properties.isIncludeRejectedValue()
                ? exception.getValue()
                : null,
            null
        );
    }

    private ErrorDetail toDetail(
        String field,
        Object rejectedValue,
        MessageSourceResolvable error
    ) {
        String[] codes = error.getCodes();
        String rawCode = codes == null
            || codes.length == 0
            || codes[0] == null
            || codes[0].isBlank()
            ? ValidationCode.FIELD_INVALID.getKey()
            : codes[0];
        String code = stableCode(rawCode, ValidationCode.FIELD_INVALID.getKey());
        String fallback = error.getDefaultMessage() == null
            || error.getDefaultMessage().isBlank()
            ? "Invalid value."
            : error.getDefaultMessage();
        return new ErrorDetail(
            field,
            code,
            i18n.getOrDefault(code, fallback, field),
            properties.isIncludeRejectedValue() ? rejectedValue : null,
            null
        );
    }

    private static String stableCode(String rawCode, String fallback) {
        if (rawCode == null || rawCode.isBlank()) {
            return fallback;
        }
        Matcher matcher = STANDARD_CONSTRAINT_PATTERN.matcher(rawCode);
        return matcher.find()
            ? STANDARD_CONSTRAINT_CODES.get(matcher.group(1)).getKey()
            : rawCode;
    }
}
