package com.company.platform.exchange.domain.exception;

import lombok.Getter;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Getter
public final class OutboundHttpException extends OutboundCallException {

    private final String method;
    private final URI uri;
    private final Integer status;
    private final Map<String, List<String>> headers;
    private final String responseBody;
    private final int retryCount;
    private final Duration duration;
    private final boolean retryable;
    private final boolean recordable;

    public OutboundHttpException(
        String clientName, String method, URI uri, Integer status,
        Map<String, List<String>> headers, String responseBody, int retryCount,
        Duration duration, boolean retryable, Throwable cause
    ) {
        this(clientName, method, uri, status, headers, responseBody, retryCount,
            duration, retryable,
            status == null ? retryable : status == 408 || status >= 500, cause);
    }

    public OutboundHttpException(
        String clientName, String method, URI uri, Integer status,
        Map<String, List<String>> headers, String responseBody, int retryCount,
        Duration duration, boolean retryable, boolean recordable, Throwable cause
    ) {
        super("EXCHANGE.HTTP_FAILED", clientName,
            "HTTP outbound call failed: client=" + clientName + ", method=" + method
                + ", target=" + uri + ", status=" + status, cause);
        this.method = method;
        this.uri = uri;
        this.status = status;
        this.headers = headers == null ? Map.of() : Map.copyOf(headers);
        this.responseBody = responseBody;
        this.retryCount = retryCount;
        this.duration = duration;
        this.retryable = retryable;
        this.recordable = recordable;
    }
}
