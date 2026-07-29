package com.company.platform.core.exception.handler.helper;

import com.company.platform.core.exception.code.ValidationCode;
import com.company.platform.core.i18n.I18nService;
import com.company.platform.core.rest.response.ErrorDetail;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.exc.InvalidDefinitionException;
import tools.jackson.databind.exc.InvalidFormatException;
import tools.jackson.databind.exc.InvalidNullException;
import tools.jackson.databind.exc.MismatchedInputException;
import tools.jackson.databind.exc.UnrecognizedPropertyException;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Converts Jackson 3 failures to a stable, localized and non-sensitive API error.
 *
 * <p>Raw Jackson messages and rejected values are deliberately never returned
 * because they can expose implementation details or request secrets.</p>
 */
@RequiredArgsConstructor
public final class JsonExceptionHelper {

    private static final Set<Class<?>> BOOLEAN_TYPES = types(
        boolean.class, Boolean.class);
    private static final Set<Class<?>> INTEGER_TYPES = types(
        byte.class, Byte.class,
        short.class, Short.class,
        int.class, Integer.class,
        long.class, Long.class,
        BigInteger.class
    );
    private static final Set<Class<?>> NUMBER_TYPES = types(
        float.class, Float.class,
        double.class, Double.class,
        BigDecimal.class
    );
    private static final Set<Class<?>> DATE_TIME_TYPES = types(
        LocalDateTime.class, OffsetDateTime.class, Instant.class);

    private final I18nService i18n;

    public List<ErrorDetail> extractJsonErrorDetails(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof InvalidDefinitionException definitionException) {
                return List.of(detail(
                    "requestBody",
                    ValidationCode.REQUEST_DEFINITION_INVALID,
                    safeTypeName(definitionException.getType().getRawClass())
                ));
            }
            if (current instanceof JacksonException mappingException) {
                String field = buildFieldPath(mappingException);
                JsonFailure failure = classify(mappingException, field);
                return List.of(detail(
                    failure.getField(),
                    failure.getCode(),
                    failure.getArguments()
                ));
            }
            current = current.getCause();
        }
        return List.of(detail(
            "requestBody",
            ValidationCode.JSON_MALFORMED
        ));
    }

    private JsonFailure classify(JacksonException exception, String field) {
        if (exception instanceof UnrecognizedPropertyException) {
            return failure(
                field,
                ValidationCode.FIELD_UNKNOWN,
                field
            );
        }

        Class<?> targetType = targetType(exception);
        if (exception instanceof InvalidNullException) {
            return failure(field, ValidationCode.FIELD_REQUIRED, field);
        }
        if (isBoolean(targetType)) {
            return failure(field, ValidationCode.FIELD_BOOLEAN, field);
        }
        if (targetType == LocalDate.class) {
            return failure(field, ValidationCode.FIELD_DATE, field);
        }
        if (DATE_TIME_TYPES.contains(targetType)) {
            return failure(field, ValidationCode.FIELD_DATE_TIME, field);
        }
        if (targetType == UUID.class) {
            return failure(field, ValidationCode.FIELD_UUID, field);
        }
        if (targetType != null && targetType.isEnum()) {
            return failure(
                field,
                ValidationCode.FIELD_ENUM,
                field,
                supportedEnumValues(targetType)
            );
        }
        if (isInteger(targetType)) {
            return failure(field, ValidationCode.FIELD_INTEGER, field);
        }
        if (isNumber(targetType)) {
            return failure(field, ValidationCode.FIELD_NUMBER, field);
        }
        if (targetType == String.class
            && "validation.string".equals(exception.getOriginalMessage())) {
            return failure(
                field,
                ValidationCode.FIELD_TYPE,
                field,
                safeTypeName(targetType)
            );
        }
        if (isFormatFailure(exception)) {
            return failure(field, ValidationCode.FIELD_FORMAT, field);
        }
        if (targetType != null) {
            return failure(
                field,
                ValidationCode.FIELD_TYPE,
                field,
                safeTypeName(targetType)
            );
        }
        return failure("requestBody", ValidationCode.JSON_MALFORMED);
    }

    private ErrorDetail detail(
        String field,
        ValidationCode code,
        Object... arguments
    ) {
        return new ErrorDetail(
            field,
            code.getKey(),
            i18n.get(code, arguments),
            null,
            null
        );
    }

    private static JsonFailure failure(
        String field,
        ValidationCode code,
        Object... arguments
    ) {
        return new JsonFailure(field, code, arguments);
    }

    private static String buildFieldPath(JacksonException exception) {
        StringBuilder path = new StringBuilder();
        for (JacksonException.Reference reference : exception.getPath()) {
            if (reference.getPropertyName() != null) {
                if (!path.isEmpty()) {
                    path.append(".");
                }
                path.append(reference.getPropertyName());
            }
            if (reference.getIndex() >= 0) {
                path.append("[")
                    .append(reference.getIndex())
                    .append("]");
            }
        }
        return path.isEmpty() ? "requestBody" : path.toString();
    }

    private static Class<?> targetType(JacksonException exception) {
        return exception instanceof MismatchedInputException mismatch
            ? mismatch.getTargetType()
            : null;
    }

    private static boolean isBoolean(Class<?> targetType) {
        return BOOLEAN_TYPES.contains(targetType);
    }

    private static boolean isInteger(Class<?> type) {
        return INTEGER_TYPES.contains(type);
    }

    private static boolean isNumber(Class<?> type) {
        return NUMBER_TYPES.contains(type);
    }

    private static boolean isFormatFailure(JacksonException exception) {
        return exception instanceof InvalidFormatException;
    }

    private static String supportedEnumValues(Class<?> type) {
        return Arrays.stream(type.getEnumConstants())
            .map(String::valueOf)
            .collect(Collectors.joining(", "));
    }

    private static String safeTypeName(Class<?> type) {
        return type.getSimpleName();
    }

    private static Set<Class<?>> types(Class<?>... types) {
        return Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(types)));
    }

    @Getter
    @RequiredArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
    private static final class JsonFailure {
        String field;
        ValidationCode code;
        Object[] arguments;
    }
}
