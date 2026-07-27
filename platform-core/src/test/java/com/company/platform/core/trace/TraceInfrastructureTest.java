package com.company.platform.core.trace;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Proxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class TraceInfrastructureTest {

    @Test
    void currentTraceContextReportsAvailabilityForNullBlankAndPresentIds() {
        CurrentTraceContext empty = CurrentTraceContext.empty();
        CurrentTraceContext blank = new CurrentTraceContext(" ", "span");
        CurrentTraceContext available = new CurrentTraceContext("trace", "span");

        assertThat(empty.getTraceId()).isNull();
        assertThat(empty.getSpanId()).isNull();
        assertThat(empty.isAvailable()).isFalse();
        assertThat(blank.isAvailable()).isFalse();
        assertThat(available.isAvailable()).isTrue();
        assertThat(available.getSpanId()).isEqualTo("span");
    }

    @Test
    void providerReturnsEmptyContextWithoutCurrentSpanOrSpanContext() {
        MicrometerTraceContextProvider withoutSpan =
            new MicrometerTraceContextProvider(tracer(null));
        MicrometerTraceContextProvider withoutContext =
            new MicrometerTraceContextProvider(tracer(span(null)));

        assertThat(withoutSpan.getCurrentContext().isAvailable()).isFalse();
        assertThat(withoutContext.getCurrentContext().isAvailable()).isFalse();
    }

    @Test
    void providerNormalizesTraceAndSpanIdentifiers() {
        CurrentTraceContext present = new MicrometerTraceContextProvider(
            tracer(span(context("trace-id", "span-id")))
        ).getCurrentContext();
        CurrentTraceContext normalized = new MicrometerTraceContextProvider(
            tracer(span(context(null, " ")))
        ).getCurrentContext();

        assertThat(present.getTraceId()).isEqualTo("trace-id");
        assertThat(present.getSpanId()).isEqualTo("span-id");
        assertThat(normalized.getTraceId()).isNull();
        assertThat(normalized.getSpanId()).isNull();
    }

    @Test
    void providerRejectsNullTracer() {
        assertThatNullPointerException()
            .isThrownBy(() -> new MicrometerTraceContextProvider(null))
            .withMessage("tracer must not be null");
    }

    @Test
    void traceHeadersExposeCompleteImmutableHeaderSetAndUtilityConstructor() throws Exception {
        assertThat(TraceHeaders.ALL).containsExactlyInAnyOrder(
            TraceHeaders.REQUEST_ID,
            TraceHeaders.CORRELATION_ID,
            TraceHeaders.TRACEPARENT,
            TraceHeaders.TRACESTATE,
            TraceHeaders.B3,
            TraceHeaders.B3_TRACE_ID,
            TraceHeaders.B3_SPAN_ID
        );

        Constructor<TraceHeaders> constructor = TraceHeaders.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        assertThat(constructor.newInstance()).isNotNull();
    }

    private static Tracer tracer(Span currentSpan) {
        return proxy(Tracer.class, (methodName) ->
            "currentSpan".equals(methodName) ? currentSpan : null
        );
    }

    private static Span span(TraceContext context) {
        return proxy(Span.class, (methodName) ->
            "context".equals(methodName) ? context : null
        );
    }

    private static TraceContext context(String traceId, String spanId) {
        return proxy(TraceContext.class, (methodName) -> switch (methodName) {
            case "traceId" -> traceId;
            case "spanId" -> spanId;
            default -> null;
        });
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, MethodResult result) {
        return (T) Proxy.newProxyInstance(
            type.getClassLoader(),
            new Class<?>[]{type},
            (proxy, method, arguments) -> result.value(method.getName())
        );
    }

    @FunctionalInterface
    private interface MethodResult {
        Object value(String methodName);
    }
}
