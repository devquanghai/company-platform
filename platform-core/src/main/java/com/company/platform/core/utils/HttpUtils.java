package com.company.platform.core.utils;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.company.platform.core.web.internal.adapter.servlet.CachedBodyHttpServletRequestWrapper;
import lombok.experimental.UtilityClass;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.util.Map;
import java.util.Objects;
import java.util.HashMap;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

@UtilityClass
public class HttpUtils {
    public HttpServletRequest getRequest() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs
            ? attrs.getRequest()
            : null;
    }

    public HttpServletResponse getResponse() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs
            ? attrs.getResponse()
            : null;
    }

    public String getHeader(String headerName) {
        var request = getRequest();
        if (Objects.nonNull(request)) {
            return request.getHeader(headerName);
        }
        return null;
    }

    public String getRequestBody(HttpServletRequest request) {
        if (request instanceof CachedBodyHttpServletRequestWrapper wrapper) {
            return content(wrapper.getCachedBody(), request.getCharacterEncoding());
        }
        if (request instanceof ContentCachingRequestWrapper wrapper) {
            return content(wrapper.getContentAsByteArray(), request.getCharacterEncoding());
        }
        return null;
    }

    public String getResponseBody(HttpServletResponse response) {
        if (response instanceof ContentCachingResponseWrapper wrapper) {
            return content(wrapper.getContentAsByteArray(), response.getCharacterEncoding());
        }
        return null;
    }

    public String getRequestAttribute(String name) {
        var request = getRequest();
        if (Objects.nonNull(request) && Objects.nonNull(request.getAttribute(name))) {
            return String.valueOf(request.getAttribute(name));
        }
        return null;
    }

    public static Map<String, String> getHeaders(HttpServletRequest request) {
        if (request == null) {
            request = getRequest();
        }
        if (request == null) {
            return Map.of();
        }
        Map<String, String> headers = new HashMap<>();
        var headerNames = request.getHeaderNames();
        if (headerNames == null) {
            return Map.of();
        }
        while (headerNames.hasMoreElements()) {
            String name = headerNames.nextElement();
            headers.put(name, request.getHeader(name));
        }
        return Map.copyOf(headers);
    }

    public static Map<String, String> getQueryParams(HttpServletRequest request) {
        if (request == null) {
            request = getRequest();
        }
        if (request == null) {
            return Map.of();
        }
        Map<String, String> params = new HashMap<>();
        Map<String, String[]> paramMap = request.getParameterMap();
        for (Map.Entry<String, String[]> entry : paramMap.entrySet()) {
            String key = entry.getKey();
            String[] values = entry.getValue();
            if (values == null) {
                continue;
            }
            String value = String.join(",", values);
            params.put(key, value);
        }
        return Map.copyOf(params);
    }

    private String content(byte[] bytes, String encoding) {
        if (bytes.length == 0) {
            return null;
        }
        Charset charset = encoding == null
            ? StandardCharsets.UTF_8
            : Charset.forName(encoding);
        return new String(bytes, charset);
    }
}
