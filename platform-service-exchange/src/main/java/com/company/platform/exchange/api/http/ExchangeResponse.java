package com.company.platform.exchange.api.http;

import org.springframework.http.HttpHeaders;

import java.time.Duration;
import java.util.Objects;

public final class ExchangeResponse<T> {

    private final int statusCode;
    private final HttpHeaders headers;
    private final T body;
    private final Duration duration;
    private final OutboundCallMetadata metadata;

    public ExchangeResponse(
        int statusCode, HttpHeaders headers, T body, Duration duration,
        OutboundCallMetadata metadata
    ) {
        this.statusCode = statusCode;
        this.headers = HttpHeaders.readOnlyHttpHeaders(new HttpHeaders(headers));
        this.body = body;
        this.duration = Objects.requireNonNull(duration, "duration");
        this.metadata = Objects.requireNonNull(metadata, "metadata");
    }

    public int statusCode() { return statusCode; }
    public HttpHeaders headers() { return headers; }
    public T body() { return body; }
    public Duration duration() { return duration; }
    public OutboundCallMetadata metadata() { return metadata; }
}
