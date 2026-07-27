package com.company.platform.logging.structured.customizer;

import com.company.platform.logging.domain.model.LogCategory;
import com.company.platform.logging.domain.model.LogSeverity;
import lombok.Getter;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Getter
public final class MutablePlatformLogEvent {
    private static final Set<String> RESERVED = Set.of(
        "event.name", "event.category", "event.severity", "event.message");
    private final String eventName;
    private final String message;
    private final LogSeverity severity;
    private final LogCategory category;
    private final LinkedHashMap<String, Object> fields;

    public MutablePlatformLogEvent(
        String eventName, String message, LogSeverity severity,
        LogCategory category, Map<String, ?> fields
    ) {
        this.eventName = eventName;
        this.message = message;
        this.severity = severity;
        this.category = category;
        this.fields = new LinkedHashMap<>();
        if (fields != null) {
            fields.forEach((key, value) -> {
                if (key != null && value != null && !RESERVED.contains(key)) {
                    this.fields.put(key, value);
                }
            });
        }
    }

    public void put(String name, Object value) {
        if (name != null && value != null && !RESERVED.contains(name)) {
            fields.put(name, value);
        }
    }

    public Map<String, Object> fieldsSnapshot() {
        return Map.copyOf(fields);
    }
}
