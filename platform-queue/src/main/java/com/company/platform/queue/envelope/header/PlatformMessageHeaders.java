package com.company.platform.queue.envelope.header;

import java.util.Set;

public final class PlatformMessageHeaders {
    public static final String MESSAGE_ID = "message-id";
    public static final String EVENT_ID = "event-id";
    public static final String EVENT_TYPE = "event-type";
    public static final String SCHEMA_VERSION = "schema-version";
    public static final String CORRELATION_ID = "correlation-id";
    public static final String CAUSATION_ID = "causation-id";
    public static final String SOURCE_APPLICATION = "source-application";
    public static final String OCCURRED_AT = "occurred-at";
    public static final String PUBLISHED_AT = "published-at";
    public static final String CONTENT_TYPE = "content-type";
    public static final String TRACEPARENT = "traceparent";
    public static final String TRACESTATE = "tracestate";
    public static final String DELIVERY_ATTEMPT = "delivery-attempt";
    public static final String ORIGINAL_MESSAGE_ID = "original-message-id";
    public static final String REPLAY_ID = "replay-id";

    public static final Set<String> RESERVED = Set.of(
        MESSAGE_ID, EVENT_ID, EVENT_TYPE, SCHEMA_VERSION, CORRELATION_ID,
        CAUSATION_ID, SOURCE_APPLICATION, OCCURRED_AT, PUBLISHED_AT,
        CONTENT_TYPE, TRACEPARENT, TRACESTATE, DELIVERY_ATTEMPT,
        ORIGINAL_MESSAGE_ID, REPLAY_ID,
        "__typeid__", "__contenttypeid__", "__keytypeid__"
    );

    private PlatformMessageHeaders() {
    }
}
