package com.company.platform.core.exception.handler.helper;

import com.company.platform.core.exception.code.ValidationCode;
import com.company.platform.core.i18n.I18nService;
import com.company.platform.core.rest.response.ErrorDetail;
import lombok.RequiredArgsConstructor;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.exc.InvalidDefinitionException;
import tools.jackson.databind.exc.InvalidFormatException;

import java.util.List;

@RequiredArgsConstructor
public final class JsonExceptionHelper {

    private final I18nService i18n;

    public List<ErrorDetail> extractJsonErrorDetails(
        Throwable throwable
    ) {

        Throwable current = throwable;

        while (current != null) {

            if (current instanceof InvalidDefinitionException definitionException) {

                return List.of(
                    new ErrorDetail(
                        "requestBody",
                        ValidationCode.FIELD_INVALID.getKey(),
                        simplifyInvalidDefinition(definitionException),
                        null,
                        null
                    )
                );
            }

            if (current instanceof JacksonException mappingException) {

                String field = buildFieldPath(
                    mappingException
                );

                return List.of(
                    new ErrorDetail(
                        field,
                        ValidationCode.FIELD_INVALID.getKey(),
                        resolveValidationMessage(
                            mappingException,
                            field
                        ),
                        null,
                        null
                    )
                );
            }

            current = current.getCause();
        }

        return List.of(
            new ErrorDetail(
                "requestBody",
                ValidationCode.FIELD_INVALID.getKey(),
                i18n.getOrDefault(
                    ValidationCode.FIELD_INVALID.getKey(),
                    "Invalid request body."
                ),
                null,
                null
            )
        );
    }

    private String resolveValidationMessage(
        JacksonException exception,
        String field
    ) {

        String key = exception.getOriginalMessage();

        if (exception instanceof InvalidFormatException formatException) {

            return i18n.getOrDefault(
                key,
                "Invalid value.",
                field,
                formatException.getValue()
            );
        }

        return i18n.getOrDefault(
            key,
            "Invalid value.",
            field
        );
    }

    private String simplifyInvalidDefinition(
        InvalidDefinitionException exception
    ) {

        Class<?> type = exception.getType().getRawClass();

        return i18n.getOrDefault(
            "validation.invalid.request.definition",
            "Request object definition is invalid.",
            type.getSimpleName()
        );
    }

    private String buildFieldPath(
        JacksonException exception
    ) {

        StringBuilder path = new StringBuilder();

        for (JacksonException.Reference reference : exception.getPath()) {

            if (reference.getPropertyName() != null) {

                if (!path.isEmpty()) {
                    path.append(".");
                }

                path.append(
                    reference.getPropertyName()
                );
            }

            if (reference.getIndex() >= 0) {

                path.append("[")
                    .append(reference.getIndex())
                    .append("]");
            }
        }

        return path.isEmpty()
            ? "requestBody"
            : path.toString();
    }
}
