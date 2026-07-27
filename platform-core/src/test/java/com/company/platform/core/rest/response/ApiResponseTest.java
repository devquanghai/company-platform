package com.company.platform.core.rest.response;

import com.company.platform.core.exception.error.ErrorCategory;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApiResponseTest {

    private static final OffsetDateTime TIMESTAMP =
        OffsetDateTime.of(2026, 7, 23, 8, 30, 0, 0, ZoneOffset.ofHours(7));

    @Test
    void apiErrorValidatesRequiredFieldsAndDefensivelyCopiesDetails() {
        ErrorDetail detail = ErrorDetail.of("name", "required", "Name is required");
        List<ErrorDetail> mutableDetails = new java.util.ArrayList<>(List.of(detail));
        ApiError error = new ApiError(
            "VALIDATION",
            "Invalid request",
            ErrorCategory.VALIDATION,
            mutableDetails
        );
        mutableDetails.clear();

        assertThat(error.getCode()).isEqualTo("VALIDATION");
        assertThat(error.getMessage()).isEqualTo("Invalid request");
        assertThat(error.getCategory()).isEqualTo(ErrorCategory.VALIDATION);
        assertThat(error.getDetails()).containsExactly(detail);
        assertThat(error.hasDetails()).isTrue();
        assertThat(error.toString()).contains("VALIDATION", "Invalid request");
        assertThat(error).isEqualTo(new ApiError(
            "VALIDATION",
            "Invalid request",
            ErrorCategory.VALIDATION,
            List.of(detail)
        ));
    }

    @Test
    void apiErrorFactoriesAndValidationCoverNullBlankAndEmptyCases() {
        ApiError error = ApiError.of("NOT_FOUND", "Not found", ErrorCategory.NOT_FOUND);
        ApiError nullDetails = new ApiError(
            "INTERNAL", "Internal", ErrorCategory.INTERNAL, null
        );

        assertThat(error.hasDetails()).isFalse();
        assertThat(nullDetails.getDetails()).isEmpty();
        assertThatIllegalArgumentException().isThrownBy(
            () -> new ApiError(null, "message", ErrorCategory.INTERNAL, null)
        ).withMessage("code must not be blank");
        assertThatIllegalArgumentException().isThrownBy(
            () -> new ApiError(" ", "message", ErrorCategory.INTERNAL, null)
        ).withMessage("code must not be blank");
        assertThatIllegalArgumentException().isThrownBy(
            () -> new ApiError("code", null, ErrorCategory.INTERNAL, null)
        ).withMessage("message must not be blank");
        assertThatIllegalArgumentException().isThrownBy(
            () -> new ApiError("code", " ", ErrorCategory.INTERNAL, null)
        ).withMessage("message must not be blank");
        assertThatNullPointerException().isThrownBy(
            () -> new ApiError("code", "message", null, null)
        ).withMessage("category must not be null");
    }

    @Test
    void errorDetailSupportsFactoriesMetadataAndSafeToString() {
        Map<String, Object> mutableMetadata = new HashMap<>();
        mutableMetadata.put("constraint", "NotBlank");
        Object rejected = new Object();
        ErrorDetail full = new ErrorDetail(
            "name",
            "required",
            "Name is required",
            rejected,
            mutableMetadata
        );
        mutableMetadata.clear();

        assertThat(full.getField()).isEqualTo("name");
        assertThat(full.getCode()).isEqualTo("required");
        assertThat(full.getMessage()).isEqualTo("Name is required");
        assertThat(full.getRejectedValue()).isSameAs(rejected);
        assertThat(full.getMetadata()).containsEntry("constraint", "NotBlank");
        assertThat(full.toString()).doesNotContain(rejected.toString());
        assertThat(full).isEqualTo(new ErrorDetail(
            "name", "required", "Name is required", rejected, Map.of("constraint", "NotBlank")
        ));

        assertThat(ErrorDetail.of("field", "code", "message").getRejectedValue()).isNull();
        assertThat(ErrorDetail.of("field", "code", "message", "bad").getRejectedValue())
            .isEqualTo("bad");
        assertThat(new ErrorDetail(null, null, null, null, null).getMetadata()).isEmpty();
    }

    @Test
    void responseMetadataNormalizesValuesAndCopiesAttributes() {
        Map<String, Object> mutableAttributes = new HashMap<>();
        mutableAttributes.put("tenant", "lfvn");
        ResponseMetadata metadata = new ResponseMetadata(
            "/users",
            "GET",
            "request-id",
            "correlation-id",
            "trace-id",
            "span-id",
            TIMESTAMP,
            mutableAttributes
        );
        mutableAttributes.clear();

        assertThat(metadata.getUrl()).isEqualTo("/users");
        assertThat(metadata.getMethod()).isEqualTo("GET");
        assertThat(metadata.getRequestId()).isEqualTo("request-id");
        assertThat(metadata.getCorrelationId()).isEqualTo("correlation-id");
        assertThat(metadata.getTraceId()).isEqualTo("trace-id");
        assertThat(metadata.getSpanId()).isEqualTo("span-id");
        assertThat(metadata.getTimestamp()).isEqualTo(TIMESTAMP);
        assertThat(metadata.getAttributes()).containsEntry("tenant", "lfvn");
        assertThat(metadata.hasTrace()).isTrue();
        assertThat(metadata.toString()).contains("trace-id");
        assertThat(metadata).isEqualTo(new ResponseMetadata(
            "/users", "GET", "request-id", "correlation-id", "trace-id", "span-id",
            TIMESTAMP, Map.of("tenant", "lfvn")
        ));
    }

    @Test
    void responseMetadataSupportsNullAndBlankValuesButRequiresTimestamp() {
        ResponseMetadata blank = new ResponseMetadata(
            " ", "", "\t", "\n", " ", "", TIMESTAMP, null
        );
        ResponseMetadata empty = ResponseMetadata.empty();

        assertThat(blank.getUrl()).isNull();
        assertThat(blank.getMethod()).isNull();
        assertThat(blank.getRequestId()).isNull();
        assertThat(blank.getCorrelationId()).isNull();
        assertThat(blank.getTraceId()).isNull();
        assertThat(blank.getSpanId()).isNull();
        assertThat(blank.getAttributes()).isEmpty();
        assertThat(blank.hasTrace()).isFalse();
        assertThat(empty.getTimestamp()).isNotNull();
        assertThat(empty.hasTrace()).isFalse();
        assertThatNullPointerException().isThrownBy(() -> new ResponseMetadata(
            null, null, null, null, null, null, null, null
        )).withMessage("timestamp must not be null");
    }

    @Test
    void apiResponseFactoriesCreateConsistentSuccessAndFailurePayloads() {
        ResponseMetadata metadata = new ResponseMetadata(
            "/resource", "GET", null, null, null, null, TIMESTAMP, Map.of()
        );
        ApiError error = ApiError.of("NOT_FOUND", "Not found", ErrorCategory.NOT_FOUND);

        ApiResponse<String> success = ApiResponse.success("data", metadata);
        ApiResponse<String> defaultSuccess = ApiResponse.success("data");
        ApiResponse<Void> emptySuccess = ApiResponse.success(metadata);
        ApiResponse<String> failure = ApiResponse.failure(error, metadata);
        ApiResponse<String> defaultFailure = ApiResponse.failure(error);

        assertThat(success.isSuccess()).isTrue();
        assertThat(success.getData()).isEqualTo("data");
        assertThat(success.getError()).isNull();
        assertThat(success.getMetadata()).isSameAs(metadata);
        assertThat(defaultSuccess.getMetadata()).isNotNull();
        assertThat(emptySuccess.getData()).isNull();
        assertThat(failure.isSuccess()).isFalse();
        assertThat(failure.getData()).isNull();
        assertThat(failure.getError()).isSameAs(error);
        assertThat(defaultFailure.getMetadata()).isNotNull();
        assertThat(success.toString()).contains("success=true", "data=data");
    }

    @Test
    void apiResponseConstructorEnforcesEveryInvariant() throws Exception {
        ApiError error = ApiError.of("ERROR", "Error", ErrorCategory.INTERNAL);
        ResponseMetadata metadata = ResponseMetadata.empty();

        assertPrivateConstructorFailure(true, "data", error, metadata,
            "success response must not contain error");
        assertPrivateConstructorFailure(false, "data", error, metadata,
            "failure response must not contain data");
        assertPrivateConstructorFailure(false, null, null, metadata,
            "failure response must contain error");
        assertPrivateConstructorFailure(true, null, null, null,
            "metadata must not be null");
    }

    private static void assertPrivateConstructorFailure(
        boolean success,
        Object data,
        ApiError error,
        ResponseMetadata metadata,
        String message
    ) throws Exception {
        Constructor<ApiResponse> constructor = ApiResponse.class.getDeclaredConstructor(
            boolean.class,
            Object.class,
            ApiError.class,
            ResponseMetadata.class
        );
        constructor.setAccessible(true);

        assertThatThrownBy(() -> constructor.newInstance(success, data, error, metadata))
            .isInstanceOf(InvocationTargetException.class)
            .cause()
            .isInstanceOfAny(IllegalArgumentException.class, NullPointerException.class)
            .hasMessage(message);
    }
}
