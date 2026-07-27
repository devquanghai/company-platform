package com.company.platform.core.web.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Locale;
import java.util.function.LongSupplier;

/** Adds a deterministic application duration entry to the {@code Server-Timing} header. */
public final class RequestTimingInterceptor implements HandlerInterceptor {

    static final String START_NANOS_ATTRIBUTE = RequestTimingInterceptor.class.getName() + ".startNanos";
    private final LongSupplier nanoTime;

    public RequestTimingInterceptor(LongSupplier nanoTime) {
        this.nanoTime = nanoTime;
    }

    @Override
    public boolean preHandle(
        @NonNull HttpServletRequest request,
        @NonNull HttpServletResponse response,
        @NonNull Object handler
    ) {
        request.setAttribute(START_NANOS_ATTRIBUTE, nanoTime.getAsLong());
        return true;
    }

    @Override
    public void afterCompletion(
        @NonNull HttpServletRequest request,
        @NonNull HttpServletResponse response,
        @NonNull Object handler,
        Exception exception
    ) {
        Object startedAt = request.getAttribute(START_NANOS_ATTRIBUTE);
        if (startedAt instanceof Long startNanos) {
            double durationMillis = (nanoTime.getAsLong() - startNanos) / 1_000_000.0;
            response.setHeader(
                "Server-Timing",
                String.format(Locale.ROOT, "app;dur=%.2f", durationMillis)
            );
        }
    }
}
