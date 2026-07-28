package com.company.platform.core.exception.handler.helper;

import com.company.platform.core.configuration.properties.PlatformCoreExceptionProperties;
import com.company.platform.core.exception.code.ValidationCode;
import com.company.platform.core.i18n.I18nService;
import com.company.platform.core.rest.response.ErrorDetail;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RequiredArgsConstructor
public final class ValidationExceptionHelper {

    private final PlatformCoreExceptionProperties properties;

    private final I18nService i18n;

    public ErrorDetail toDetail(
        FieldError fieldError
    ) {

        String code =
            fieldError.getCode() == null
                ? ValidationCode.FIELD_INVALID.name()
                : fieldError.getCode();

        return new ErrorDetail(
            fieldError.getField(),
            code,
            i18n.getOrDefault(
                code,
                fieldError.getDefaultMessage(),
                fieldError.getField()
            ),
            properties.isIncludeRejectedValue()
                ? fieldError.getRejectedValue()
                : null,
            null
        );
    }

    public ErrorDetail missingParameter(
        MissingServletRequestParameterException exception
    ) {

        return new ErrorDetail(
            exception.getParameterName(),
            ValidationCode.FIELD_REQUIRED.name(),
            i18n.getOrDefault(
                "validation.required",
                "Field is required.",
                exception.getParameterName()
            ),
            null,
            null
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
            ValidationCode.FIELD_INVALID.name(),
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
}
