package com.company.platform.core.trace;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

@Getter
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public final class CurrentTraceContext {

    String traceId;
    String spanId;

    public static CurrentTraceContext empty() {
        return new CurrentTraceContext(
            null,
            null
        );
    }

    public boolean isAvailable() {
        return traceId != null && !traceId.isBlank();
    }
}
