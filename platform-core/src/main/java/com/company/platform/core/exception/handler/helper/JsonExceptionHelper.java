package com.company.platform.core.exception.handler.helper;

import com.company.platform.core.exception.code.ValidationCode;
import com.company.platform.core.i18n.I18nService;
import com.company.platform.core.rest.response.ErrorDetail;
import tools.jackson.databind.JsonMappingException;
import lombok.RequiredArgsConstructor;
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
                        ValidationCode.FIELD_INVALID.name(),
                        simplifyInvalidDefinition(definitionException),
                        null,
                        null
                    )
                );
            }

            if (current instanceof JsonMappingException mappingException) {

                String field = buildFieldPath(
                    mappingException
                );

                return List.of(
                    new ErrorDetail(
                        field,
                        ValidationCode.FIELD_INVALID.name(),
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
                ValidationCode.FIELD_INVALID.name(),
                i18n.getOrDefault(
                    ValidationCode.FIELD_INVALID.name(),
                    "Invalid request body."
                ),
                null,
                null
            )
        );
    }

    private String resolveValidationMessage(
        JsonMappingException exception,
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

        Class<?> type = exception.getType();

        return i18n.getOrDefault(
            "validation.invalid.request.definition",
            "Request object definition is invalid.",
            type == null
                ? "request"
                : type.getSimpleName()
        );
    }

    private String buildFieldPath(
        JsonMappingException exception
    ) {

        StringBuilder path = new StringBuilder();

        for (JsonMappingException.Reference reference : exception.getPath()) {

            if (reference.getFieldName() != null) {

                if (!path.isEmpty()) {
                    path.append(".");
                }

                path.append(
                    reference.getFieldName()
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
