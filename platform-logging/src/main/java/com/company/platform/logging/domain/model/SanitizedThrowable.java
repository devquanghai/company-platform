package com.company.platform.logging.domain.model;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
public final class SanitizedThrowable {
    private final String type;
    private final String message;
    private final List<String> stackTrace;
    private final SanitizedThrowable cause;
    private final List<SanitizedThrowable> suppressed;

    @Builder
    public SanitizedThrowable(
        String type, String message, List<String> stackTrace,
        SanitizedThrowable cause, List<SanitizedThrowable> suppressed
    ) {
        this.type = type;
        this.message = message;
        this.stackTrace = List.copyOf(stackTrace == null ? List.of() : stackTrace);
        this.cause = cause;
        this.suppressed = List.copyOf(suppressed == null ? List.of() : suppressed);
    }
}
