package com.company.platform.core.context.internal.adapter.mdc;
import com.company.platform.core.context.RequestContextProvider;
import org.slf4j.MDC;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public final class MdcRequestContextProvider
    implements RequestContextProvider {

    public static final String REQUEST_ID_KEY = "requestId";
    public static final String CORRELATION_ID_KEY = "correlationId";

    @Override
    public String getRequestId() {
        return normalize(
            MDC.get(REQUEST_ID_KEY)
        );
    }

    @Override
    public String getCorrelationId() {
        return normalize(
            MDC.get(CORRELATION_ID_KEY)
        );
    }

    @Override
    public String getRequestUrl() {
        HttpServletRequest request = currentRequest();
        return request == null ? null : normalize(request.getRequestURI());
    }

    @Override
    public String getRequestMethod() {
        HttpServletRequest request = currentRequest();
        return request == null ? null : normalize(request.getMethod());
    }

    @Override
    public String getRemoteAddress() {
        HttpServletRequest request = currentRequest();
        return request == null ? null : normalize(request.getRemoteAddr());
    }

    @Override
    public String getUserAgent() {
        HttpServletRequest request = currentRequest();
        return request == null ? null : normalize(request.getHeader("User-Agent"));
    }

    private static HttpServletRequest currentRequest() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes
            ? attributes.getRequest()
            : null;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank()
            ? null
            : value;
    }
}
