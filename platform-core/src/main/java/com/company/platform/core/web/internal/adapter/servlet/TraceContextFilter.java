package com.company.platform.core.web.internal.adapter.servlet;

import com.company.platform.core.context.internal.adapter.mdc.MdcRequestContextProvider;
import com.company.platform.core.trace.TraceHeaders;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.jspecify.annotations.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Establishes bounded request and correlation identifiers in response headers and
 * MDC for the duration of each servlet request.
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public final class TraceContextFilter extends OncePerRequestFilter {

    private static final int MAX_IDENTIFIER_LENGTH = 128;
    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("[A-Za-z0-9._:-]{1,128}");

    @Override
    protected void doFilterInternal(
        @NonNull HttpServletRequest request,
        @NonNull HttpServletResponse response,
        @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        Map<String, String> previous = MDC.getCopyOfContextMap();
        String requestId = identifier(request.getHeader(TraceHeaders.REQUEST_ID));
        String correlationId = identifierOrDefault(
            request.getHeader(TraceHeaders.CORRELATION_ID),
            requestId
        );
        try {
            MDC.put(MdcRequestContextProvider.REQUEST_ID_KEY, requestId);
            MDC.put(MdcRequestContextProvider.CORRELATION_ID_KEY, correlationId);
            response.setHeader(TraceHeaders.REQUEST_ID, requestId);
            response.setHeader(TraceHeaders.CORRELATION_ID, correlationId);
            filterChain.doFilter(request, response);
        } finally {
            restoreMdc(previous);
        }
    }

    private static String identifier(String candidate) {
        return isSafe(candidate) ? candidate : UUID.randomUUID().toString();
    }

    private static String identifierOrDefault(String candidate, String defaultValue) {
        return isSafe(candidate) ? candidate : defaultValue;
    }

    private static boolean isSafe(String value) {
        return value != null
            && value.length() <= MAX_IDENTIFIER_LENGTH
            && SAFE_IDENTIFIER.matcher(value).matches();
    }

    private static void restoreMdc(Map<String, String> previous) {
        if (previous == null) {
            MDC.clear();
        } else {
            MDC.setContextMap(previous);
        }
    }
}
