package com.company.platform.core.exception;

import com.company.platform.core.exception.error.ErrorCategory;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class PlatformExceptionTest {

    @Test
    void builderCarriesImmutableContextAndCause() {
        RuntimeException cause = new RuntimeException("database unavailable");
        Map<String, Object> parameters = new HashMap<>();
        Map<String, Object> metadata = new HashMap<>();
        parameters.put("id", 42);
        metadata.put("retryable", true);

        PlatformException exception = PlatformException.builder(
                "RESOURCE.NOT_FOUND",
                ErrorCategory.NOT_FOUND,
                "Resource was not found"
            )
            .parameters(parameters)
            .metadata(metadata)
            .cause(cause)
            .build();
        parameters.clear();
        metadata.clear();

        assertThat(exception.getMessage()).isEqualTo("Resource was not found");
        assertThat(exception.getCause()).isSameAs(cause);
        assertThat(exception.errorCode()).isEqualTo("RESOURCE.NOT_FOUND");
        assertThat(exception.category()).isEqualTo(ErrorCategory.NOT_FOUND);
        assertThat(exception.parameters()).containsEntry("id", 42);
        assertThat(exception.metadata()).containsEntry("retryable", true);
    }

    @Test
    void builderNormalizesNullMaps() {
        PlatformException exception = PlatformException.builder(
                "BUSINESS",
                ErrorCategory.BUSINESS,
                "Business rule"
            )
            .parameters(null)
            .metadata(null)
            .build();

        assertThat(exception.parameters()).isEmpty();
        assertThat(exception.metadata()).isEmpty();
        assertThat(exception.getCause()).isNull();
    }

    @Test
    void builderRequiresMessageCodeAndCategory() {
        assertThatNullPointerException().isThrownBy(() ->
            PlatformException.builder("CODE", ErrorCategory.INTERNAL, null).build()
        ).withMessage("message");
        assertThatNullPointerException().isThrownBy(() ->
            PlatformException.builder(null, ErrorCategory.INTERNAL, "message").build()
        ).withMessage("errorCode");
        assertThatNullPointerException().isThrownBy(() ->
            PlatformException.builder("CODE", null, "message").build()
        ).withMessage("category");
    }

    @Test
    void semanticExceptionsExposeTheirExpectedCategories() {
        RuntimeException cause = new RuntimeException("root cause");

        assertException(
            new PlatformBusinessException("BUSINESS", "message"),
            "BUSINESS",
            ErrorCategory.BUSINESS,
            null
        );
        assertException(
            new PlatformConflictException("CONFLICT", "message"),
            "CONFLICT",
            ErrorCategory.CONFLICT,
            null
        );
        assertException(
            new PlatformForbiddenException("FORBIDDEN", "message"),
            "FORBIDDEN",
            ErrorCategory.AUTHORIZATION,
            null
        );
        assertException(
            new PlatformInfrastructureException("INFRASTRUCTURE", "message", cause),
            "INFRASTRUCTURE",
            ErrorCategory.INFRASTRUCTURE,
            cause
        );
        assertException(
            new PlatformIntegrationException("INTEGRATION", "message", cause),
            "INTEGRATION",
            ErrorCategory.INTEGRATION,
            cause
        );
        assertException(
            new PlatformNotFoundException("NOT_FOUND", "message"),
            "NOT_FOUND",
            ErrorCategory.NOT_FOUND,
            null
        );
        assertException(
            new PlatformTimeoutException("TIMEOUT", "message", cause),
            "TIMEOUT",
            ErrorCategory.TIMEOUT,
            cause
        );
        assertException(
            new PlatformUnauthorizedException("UNAUTHORIZED", "message"),
            "UNAUTHORIZED",
            ErrorCategory.AUTHENTICATION,
            null
        );
        assertException(
            new PlatformValidationException("VALIDATION", "message"),
            "VALIDATION",
            ErrorCategory.VALIDATION,
            null
        );
    }

    @Test
    void errorCategoriesExposeStableHttpStatusAndCodes() {
        assertThat(ErrorCategory.values()).allSatisfy(category -> {
            assertThat(category.getHttpStatus()).isNotNull();
            assertThat(category.getErrorCode()).isEqualTo(category.name());
        });
    }

    private static void assertException(
        PlatformException exception,
        String errorCode,
        ErrorCategory category,
        Throwable cause
    ) {
        assertThat(exception.errorCode()).isEqualTo(errorCode);
        assertThat(exception.category()).isEqualTo(category);
        assertThat(exception.getMessage()).isEqualTo("message");
        assertThat(exception.getCause()).isSameAs(cause);
        assertThat(exception.parameters()).isEmpty();
        assertThat(exception.metadata()).isEmpty();
    }
}
