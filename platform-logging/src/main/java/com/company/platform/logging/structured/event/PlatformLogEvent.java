package com.company.platform.logging.structured.event;

import com.company.platform.logging.domain.model.LogCategory;
import com.company.platform.logging.domain.model.LogSeverity;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@Getter
public final class PlatformLogEvent {
    private final String eventName;
    private final String message;
    private final LogSeverity severity;
    private final LogCategory category;
    private final OffsetDateTime timestamp;
    private final Map<String, Object> fields;

    public PlatformLogEvent(
        String eventName, String message, LogSeverity severity,
        LogCategory category, OffsetDateTime timestamp, Map<String, ?> fields
    ) {
        this.eventName = eventName;
        this.message = message;
        this.severity = severity;
        this.category = category;
        this.timestamp = timestamp;
        LinkedHashMap<String, Object> copy = new LinkedHashMap<>();
        if (fields != null) {
            fields.forEach((key, value) -> {
                if (key != null && value != null) {
                    copy.put(key, value);
                }
            });
        }
        this.fields = Collections.unmodifiableMap(copy);
    }
}
