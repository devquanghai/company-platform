package com.company.platform.logging.domain.model;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public final class MaskingContext {
    private final String fieldName;
    private final PiiType piiType;
    private final int visiblePrefix;
    private final int visibleSuffix;
    @Builder.Default
    private final String substitution = "***";
    private final boolean preserveDomain;
    private final String strategyBean;
}
