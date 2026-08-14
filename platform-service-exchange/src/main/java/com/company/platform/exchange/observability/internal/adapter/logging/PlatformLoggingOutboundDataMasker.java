package com.company.platform.exchange.observability.internal.adapter.logging;

import com.company.platform.exchange.observability.logging.OutboundDataMasker;
import com.company.platform.logging.api.masking.DataMaskingService;
import org.springframework.http.HttpHeaders;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Map;

public final class PlatformLoggingOutboundDataMasker implements OutboundDataMasker {
    private static final int MAX_SAFE_LOG_BODY_CHARACTERS = 1_048_576;
    private static final String OVERSIZED = "<oversized-not-logged>";
    private final DataMaskingService masking;

    public PlatformLoggingOutboundDataMasker(DataMaskingService masking) {
        this.masking = masking;
    }

    @Override
    public HttpHeaders maskHeaders(HttpHeaders headers) {
        HttpHeaders sanitized = new HttpHeaders();
        headers.forEach((name, values) -> sanitized.put(
            name, values.stream().map(value -> masking.maskValue(name, value)).toList()));
        return HttpHeaders.readOnlyHttpHeaders(sanitized);
    }

    @Override
    public URI maskUri(URI uri) {
        UriComponentsBuilder safe = UriComponentsBuilder.newInstance()
            .scheme(uri.getScheme()).host(uri.getHost());
        if (uri.getPort() >= 0) {
            safe.port(uri.getPort());
        }
        String path = uri.getRawPath();
        safe.path(path == null || path.isBlank() || "/".equals(path)
            ? "/" : "/[redacted]");
        return safe.build().encode().toUri();
    }

    @Override
    public String maskBody(Object body, int maxLength) {
        if (body == null) {
            return null;
        }
        String sanitized;
        if (body instanceof String text) {
            String trimmed = text.stripLeading();
            if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
                try {
                    sanitized = masking.sanitizeJson(text);
                } catch (RuntimeException ignored) {
                    sanitized = masking.sanitizeMessage(text);
                }
            } else {
                sanitized = masking.sanitizeMessage(text);
            }
        } else {
            sanitized = String.valueOf(masking.sanitize(body));
        }
        return sanitized.length() <= MAX_SAFE_LOG_BODY_CHARACTERS ? sanitized : OVERSIZED;
    }

    @Override
    public Map<String, Object> maskAttributes(Map<String, ?> attributes) {
        return masking.sanitizeFields(attributes);
    }
}
