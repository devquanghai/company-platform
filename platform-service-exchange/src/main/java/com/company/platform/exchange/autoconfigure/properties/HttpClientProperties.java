package com.company.platform.exchange.autoconfigure.properties;

import lombok.Getter;
import lombok.Setter;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/** @deprecated Use minimal {@code base-url} and native Boot HTTP configuration. */
@Deprecated
@Getter @Setter
public class HttpClientProperties {
    private String baseUrl;
    private Duration connectTimeout = Duration.ofSeconds(3);
    private Duration connectionRequestTimeout = Duration.ofSeconds(2);
    private Duration responseTimeout = Duration.ofSeconds(10);
    private boolean allowAbsoluteUri;
    private Map<String, String> defaultHeaders = new LinkedHashMap<>();
    private HttpPoolProperties pool = new HttpPoolProperties();
}
