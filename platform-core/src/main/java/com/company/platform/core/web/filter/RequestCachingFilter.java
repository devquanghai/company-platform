package com.company.platform.core.web.filter;

import com.company.platform.core.configuration.properties.PlatformWebProperties;
import com.company.platform.core.web.wrapper.CachedBodyHttpServletRequestWrapper;
import com.company.platform.core.web.wrapper.RequestBodyCachingLimitExceededException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Objects;

/** Makes request bodies repeatable for downstream validation, auditing, and logging. */
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public final class RequestCachingFilter extends OncePerRequestFilter {

    private final PlatformWebProperties properties;

    public RequestCachingFilter(PlatformWebProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String contentType = request.getContentType();
        return request instanceof CachedBodyHttpServletRequestWrapper
            || request.getContentLengthLong() == 0
            || (contentType != null && contentType.toLowerCase().startsWith("multipart/"));
    }

    @Override
    protected void doFilterInternal(
        @NonNull HttpServletRequest request,
        @NonNull HttpServletResponse response,
        @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            filterChain.doFilter(
                new CachedBodyHttpServletRequestWrapper(
                    request
                ),
                response
            );
        } catch (RequestBodyCachingLimitExceededException exception) {
            response.sendError(
                HttpStatus.CONTENT_TOO_LARGE.value(),
                exception.getMessage()
            );
        }
    }
}
