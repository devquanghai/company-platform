package com.company.platform.logging.domain.model;

import lombok.Builder;
import lombok.Getter;

import java.util.Map;

@Getter
public final class LogContext {
    private final Map<String, String> values;

    @Builder
    public LogContext(Map<String, String> values) {
        this.values = Map.copyOf(values == null ? Map.of() : values);
    }
}
