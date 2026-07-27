package com.company.platform.exchange.observability.logging;

import org.springframework.http.HttpHeaders;

import java.net.URI;
import java.util.Map;

public interface OutboundDataMasker {
    HttpHeaders maskHeaders(HttpHeaders headers);
    URI maskUri(URI uri);
    String maskBody(Object body, int maxLength);
    Map<String, Object> maskAttributes(Map<String, ?> attributes);
}
