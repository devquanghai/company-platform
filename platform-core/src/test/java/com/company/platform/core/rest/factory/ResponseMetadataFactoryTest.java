package com.company.platform.core.rest.factory;

import com.company.platform.core.context.RequestContextProvider;
import com.company.platform.core.rest.response.ResponseMetadata;
import com.company.platform.core.time.SystemTimeProvider;
import com.company.platform.core.trace.CurrentTraceContext;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class ResponseMetadataFactoryTest {

    private static final SystemTimeProvider TIME = new SystemTimeProvider(
        Clock.fixed(Instant.parse("2026-07-22T00:00:00Z"), ZoneId.of("UTC")),
        ZoneId.of("UTC")
    );

    @Test
    void createsMetadataFromRequestContextAndAllOverloads() {
        ResponseMetadataFactory factory = factory(
            "request-1", "correlation-1", null, null, "/current", "POST");
        assertThat(factory.create().getRequestId()).isEqualTo("request-1");
        assertThat(factory.create().getUrl()).isEqualTo("/current");
        assertThat(factory.create().getMethod()).isEqualTo("POST");
        assertThat(factory.create(Map.of("tenant", "company")).getAttributes())
            .containsEntry("tenant", "company");

        ResponseMetadata metadata = factory.create("/orders", "GET");
        assertThat(metadata.getUrl()).isEqualTo("/orders");
        assertThat(metadata.getMethod()).isEqualTo("GET");
    }

    @Test
    void fallsBackToTraceThenGeneratedRequestId() {
        ResponseMetadata traced = factory(" ", null, "trace-1", "span-1", null, null).create();
        assertThat(traced.getRequestId()).isEqualTo("trace-1");
        assertThat(traced.getCorrelationId()).isEqualTo("trace-1");
        assertThat(traced.getSpanId()).isEqualTo("span-1");

        ResponseMetadata generated = factory(null, " ", null, null, null, null)
            .create(null, null, null);
        assertThat(generated.getRequestId()).isNotBlank();
        assertThat(generated.getCorrelationId()).isEqualTo(generated.getRequestId());
        assertThat(generated.getAttributes()).isEmpty();
    }

    @Test
    void validatesDependencies() {
        RequestContextProvider request = request(null, null);
        assertThatNullPointerException().isThrownBy(() -> new ResponseMetadataFactory(null, CurrentTraceContext::empty, TIME));
        assertThatNullPointerException().isThrownBy(() -> new ResponseMetadataFactory(request, null, TIME));
        assertThatNullPointerException().isThrownBy(() -> new ResponseMetadataFactory(request, CurrentTraceContext::empty, null));
    }

    private static ResponseMetadataFactory factory(
        String requestId,
        String correlationId,
        String traceId,
        String spanId,
        String url,
        String method
    ) {
        return new ResponseMetadataFactory(
            request(requestId, correlationId, url, method),
            () -> new CurrentTraceContext(traceId, spanId),
            TIME
        );
    }

    private static RequestContextProvider request(String requestId, String correlationId) {
        return request(requestId, correlationId, null, null);
    }

    private static RequestContextProvider request(
        String requestId,
        String correlationId,
        String url,
        String method
    ) {
        return new RequestContextProvider() {
            public String getRequestId() { return requestId; }
            public String getCorrelationId() { return correlationId; }
            public String getRequestUrl() { return url; }
            public String getRequestMethod() { return method; }
        };
    }
}
