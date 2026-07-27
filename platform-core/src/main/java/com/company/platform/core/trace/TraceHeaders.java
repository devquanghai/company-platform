package com.company.platform.core.trace;

import java.util.Set;

public final class TraceHeaders {
    public static final String REQUEST_ID = "X-Request-Id";
    public static final String CORRELATION_ID = "X-Correlation-Id";
    public static final String TRACEPARENT = "traceparent";
    public static final String TRACESTATE = "tracestate";
    public static final String B3 = "b3";
    public static final String B3_TRACE_ID = "X-B3-TraceId";
    public static final String B3_SPAN_ID = "X-B3-SpanId";
    public static final Set<String> ALL = Set.of(REQUEST_ID, CORRELATION_ID, TRACEPARENT, TRACESTATE,
            B3, B3_TRACE_ID, B3_SPAN_ID);

    private TraceHeaders() {
    }
}
