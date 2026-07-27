package com.company.platform.exchange.autoconfigure.properties;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import static lombok.AccessLevel.PRIVATE;

@Getter
@Setter
@FieldDefaults(level = PRIVATE)
public class HttpClientProperties {

    String baseUrl;
    Duration connectTimeout = Duration.ofSeconds(3);
    Duration connectionRequestTimeout = Duration.ofSeconds(2);
    Duration responseTimeout = Duration.ofSeconds(10);
    boolean allowAbsoluteUri;
    Map<String, String> defaultHeaders = new LinkedHashMap<>();
    HttpPoolProperties pool = new HttpPoolProperties();
}
