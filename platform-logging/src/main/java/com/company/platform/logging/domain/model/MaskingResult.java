package com.company.platform.logging.domain.model;

import lombok.Getter;

@Getter
public final class MaskingResult {
    private final MaskingOutcome outcome;
    private final String value;

    private MaskingResult(MaskingOutcome outcome, String value) {
        this.outcome = outcome;
        this.value = value;
    }

    public static MaskingResult masked(String value) {
        return new MaskingResult(MaskingOutcome.MASKED, value);
    }

    public static MaskingResult removed() {
        return new MaskingResult(MaskingOutcome.REMOVED, null);
    }

    public static MaskingResult unchanged(String value) {
        return new MaskingResult(MaskingOutcome.UNCHANGED, value);
    }
}
