package com.company.platform.exchange.autoconfigure.properties;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.util.LinkedHashSet;
import java.util.Set;

import static lombok.AccessLevel.PRIVATE;

@Getter
@Setter
@FieldDefaults(level = PRIVATE)
public class LoggingProperties {

    boolean enabled = true;
    boolean curlEnabled;
    boolean requestHeadersEnabled = true;
    boolean responseHeadersEnabled = true;
    boolean requestBodyEnabled;
    boolean responseBodyEnabled;
    Set<String> sensitiveHeaders = new LinkedHashSet<>();
    Set<String> sensitiveFields = new LinkedHashSet<>();
    Set<String> sensitiveQueryParameters = new LinkedHashSet<>();
}
