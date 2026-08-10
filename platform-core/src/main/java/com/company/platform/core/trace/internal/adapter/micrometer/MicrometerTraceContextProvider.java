package com.company.platform.core.trace.internal.adapter.micrometer;

import com.company.platform.core.trace.CurrentTraceContext;
import com.company.platform.core.trace.TraceContextProvider;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;

import java.util.Objects;

public final class MicrometerTraceContextProvider
    implements TraceContextProvider {

    private final Tracer tracer;

    public MicrometerTraceContextProvider(Tracer tracer) {
        this.tracer = Objects.requireNonNull(
            tracer,
            "tracer must not be null"
        );
    }

    @Override
    public CurrentTraceContext getCurrentContext() {

        Span span = tracer.currentSpan();

        if (span == null) {
            return CurrentTraceContext.empty();
        }

        TraceContext context = span.context();

        if (context == null) {
            return CurrentTraceContext.empty();
        }

        return new CurrentTraceContext(
            normalize(context.traceId()),
            normalize(context.spanId())
        );
    }

    private static String normalize(String value) {
        return value == null || value.isBlank()
            ? null
            : value;
    }
}
