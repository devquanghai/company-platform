package com.company.platform.exchange.api.http;

import lombok.Builder;
import lombok.Getter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

@Getter
public final class ExchangeRequest {

    private final String clientName;
    private final HttpMethod method;
    private final String path;
    private final Map<String, Object> queryParameters;
    private final Map<String, Object> pathVariables;
    private final HttpHeaders headers;
    private final Map<String, String> cookies;
    private final Object body;
    private final MediaType contentType;
    private final java.util.List<MediaType> accept;
    private final Duration requestTimeout;
    private final String idempotencyKey;
    private final boolean idempotent;
    private final Map<String, Object> auditAttributes;
    private final Boolean loggingEnabled;
    private final Boolean resilienceEnabled;

    @Builder
    public ExchangeRequest(
        String clientName, HttpMethod method, String path,
        Map<String, ?> queryParameters, Map<String, ?> pathVariables,
        HttpHeaders headers, Map<String, String> cookies, Object body,
        MediaType contentType, java.util.List<MediaType> accept,
        Duration requestTimeout, String idempotencyKey, boolean idempotent,
        Map<String, ?> auditAttributes, Boolean loggingEnabled,
        Boolean resilienceEnabled
    ) {
        this.clientName = Objects.requireNonNull(clientName, "clientName");
        this.method = Objects.requireNonNull(method, "method");
        this.path = Objects.requireNonNull(path, "path");
        this.queryParameters = copy(queryParameters);
        this.pathVariables = copy(pathVariables);
        this.headers = HttpHeaders.readOnlyHttpHeaders(
            headers == null ? new HttpHeaders() : new HttpHeaders(headers));
        this.cookies = Map.copyOf(cookies == null ? Map.of() : cookies);
        this.body = body;
        this.contentType = contentType;
        this.accept = java.util.List.copyOf(accept == null ? java.util.List.of() : accept);
        this.requestTimeout = requestTimeout;
        this.idempotencyKey = idempotencyKey;
        this.idempotent = idempotent;
        this.auditAttributes = copy(auditAttributes);
        this.loggingEnabled = loggingEnabled;
        this.resilienceEnabled = resilienceEnabled;
    }

    private static Map<String, Object> copy(Map<String, ?> source) {
        return Map.copyOf(source == null ? Map.of() : new LinkedHashMap<>(source));
    }
}
