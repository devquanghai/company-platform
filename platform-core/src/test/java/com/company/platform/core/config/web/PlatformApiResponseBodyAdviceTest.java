package com.company.platform.core.config.web;

import com.company.platform.core.context.RequestContextProvider;
import com.company.platform.core.exception.error.ErrorCategory;
import com.company.platform.core.rest.factory.ResponseMetadataFactory;
import com.company.platform.core.rest.response.ApiError;
import com.company.platform.core.rest.response.ApiResponse;
import com.company.platform.core.rest.response.ResponseMetadata;
import com.company.platform.core.time.SystemTimeProvider;
import com.company.platform.core.trace.CurrentTraceContext;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class PlatformApiResponseBodyAdviceTest {

    @Test
    void enrichesSuccessAndFailureResponsesWithoutReplacingExistingMetadata() {
        PlatformApiResponseBodyAdvice advice =
            new PlatformApiResponseBodyAdvice(metadataFactory());

        assertThat(advice.supports(null, null)).isTrue();

        ApiResponse<String> success = ApiResponse.success("created");
        Object enrichedBody = advice.beforeBodyWrite(
            success, null, null, null, null, null);
        assertThat(enrichedBody).isInstanceOfSatisfying(ApiResponse.class, enriched -> {
            assertThat(enriched.isSuccess()).isTrue();
            assertThat(enriched.getData()).isEqualTo("created");
            assertThat(enriched.getMetadata().getUrl()).isEqualTo("/orders");
            assertThat(enriched.getMetadata().getMethod()).isEqualTo("POST");
            assertThat(enriched.getMetadata().getRequestId()).isEqualTo("request-1");
            assertThat(enriched.getMetadata().getCorrelationId()).isEqualTo("correlation-1");
            assertThat(enriched.getMetadata().getTraceId()).isEqualTo("trace-1");
            assertThat(enriched.getMetadata().getSpanId()).isEqualTo("span-1");
        });

        ApiError error = ApiError.of("ORDER.INVALID", "Invalid order", ErrorCategory.BUSINESS);
        ApiResponse<Void> failure = ApiResponse.failure(error);
        ApiResponse<?> enrichedFailure = (ApiResponse<?>) advice.beforeBodyWrite(
            failure, null, null, null, null, null);
        assertThat(enrichedFailure.isSuccess()).isFalse();
        assertThat(enrichedFailure.getError()).isSameAs(error);
        assertThat(enrichedFailure.getMetadata().getUrl()).isEqualTo("/orders");

        ResponseMetadata explicit = metadataFactory().create(Map.of("source", "controller"));
        ApiResponse<String> alreadyEnriched = ApiResponse.success("ok", explicit);
        assertThat(advice.beforeBodyWrite(
            alreadyEnriched, null, null, null, null, null)).isSameAs(alreadyEnriched);

        assertPreserved(advice, metadata(null, "GET", null, null, null, null));
        assertPreserved(advice, metadata(null, null, "request", null, null, null));
        assertPreserved(advice, metadata(null, null, null, "correlation", null, null));
        assertPreserved(advice, metadata(null, null, null, null, "trace", "span"));

        assertThat(advice.beforeBodyWrite(
            "plain body", null, null, null, null, null)).isEqualTo("plain body");
    }

    @Test
    void validatesRequiredDependency() {
        assertThatNullPointerException()
            .isThrownBy(() -> new PlatformApiResponseBodyAdvice(null));
    }

    private static ResponseMetadataFactory metadataFactory() {
        RequestContextProvider request = new RequestContextProvider() {
            @Override public String getRequestId() { return "request-1"; }
            @Override public String getCorrelationId() { return "correlation-1"; }
            @Override public String getRequestUrl() { return "/orders"; }
            @Override public String getRequestMethod() { return "POST"; }
        };
        return new ResponseMetadataFactory(
            request,
            () -> new CurrentTraceContext("trace-1", "span-1"),
            new SystemTimeProvider(
                Clock.fixed(Instant.parse("2026-07-28T12:00:00Z"), ZoneOffset.UTC),
                ZoneOffset.UTC
            )
        );
    }

    private static void assertPreserved(
        PlatformApiResponseBodyAdvice advice,
        ResponseMetadata metadata
    ) {
        ApiResponse<String> response = ApiResponse.success("ok", metadata);
        assertThat(advice.beforeBodyWrite(response, null, null, null, null, null))
            .isSameAs(response);
    }

    private static ResponseMetadata metadata(
        String url,
        String method,
        String requestId,
        String correlationId,
        String traceId,
        String spanId
    ) {
        return new ResponseMetadata(
            url,
            method,
            requestId,
            correlationId,
            traceId,
            spanId,
            OffsetDateTime.parse("2026-07-28T12:00:00Z"),
            Map.of()
        );
    }
}
