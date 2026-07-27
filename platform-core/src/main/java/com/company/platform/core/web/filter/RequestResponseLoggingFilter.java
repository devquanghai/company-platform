package com.company.platform.core.web.filter;

import com.company.platform.core.configuration.properties.PlatformWebProperties;
import com.company.platform.core.web.wrapper.CachedBodyHttpServletRequestWrapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.jspecify.annotations.NonNull;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Logs one HTTP completion event and, only when explicitly enabled, bounded textual
 * request/response payloads. Headers and query strings are intentionally omitted.
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE + 30)
public final class RequestResponseLoggingFilter extends OncePerRequestFilter {

    private final PlatformWebProperties properties;

    public RequestResponseLoggingFilter(PlatformWebProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(
        @NonNull HttpServletRequest request,
        @NonNull HttpServletResponse response,
        @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        long startedAt = System.nanoTime();
        if (!properties.isIncludePayload()) {
            try {
                filterChain.doFilter(request, response);
            } finally {
                logSummary(request, response, startedAt, null, null);
            }
            return;
        }

        HttpServletRequest requestToUse = request instanceof CachedBodyHttpServletRequestWrapper
            ? request
            : new ContentCachingRequestWrapper(request, properties.getMaxPayloadLength());
        ContentCachingResponseWrapper cachedResponse = new ContentCachingResponseWrapper(response);
        try {
            filterChain.doFilter(requestToUse, cachedResponse);
        } finally {
            logSummary(
                request,
                response,
                startedAt,
                payload(requestBody(requestToUse), request.getContentType()),
                payload(cachedResponse.getContentAsByteArray(), response.getContentType())
            );
            cachedResponse.copyBodyToResponse();
        }
    }

    private static byte[] requestBody(HttpServletRequest request) {
        if (request instanceof CachedBodyHttpServletRequestWrapper wrapper) {
            return wrapper.getCachedBody();
        }
        return ((ContentCachingRequestWrapper) request).getContentAsByteArray();
    }

    private void logSummary(
        HttpServletRequest request,
        HttpServletResponse response,
        long startedAt,
        String requestPayload,
        String responsePayload
    ) {
        long durationMillis = (System.nanoTime() - startedAt) / 1_000_000;
        log.info(
            "HTTP {} {} completed status={} durationMs={} requestPayload={} responsePayload={}",
            request.getMethod(),
            request.getRequestURI(),
            response.getStatus(),
            durationMillis,
            requestPayload,
            responsePayload
        );
    }

    private String payload(byte[] content, String contentType) {
        if (content.length == 0 || !isTextual(contentType)) {
            return null;
        }
        String value = new String(content, StandardCharsets.UTF_8)
            .replace('\r', ' ')
            .replace('\n', ' ')
            .replace('\t', ' ');
        int limit = Math.min(value.length(), Math.max(0, properties.getMaxPayloadLength()));
        return value.substring(0, limit);
    }

    private static boolean isTextual(String contentType) {
        if (contentType == null) {
            return false;
        }
        String normalized = contentType.toLowerCase(Locale.ROOT);
        return normalized.startsWith(MediaType.TEXT_PLAIN_VALUE)
            || normalized.contains("json")
            || normalized.contains("xml")
            || normalized.contains("x-www-form-urlencoded");
    }
}
