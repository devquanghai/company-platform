package com.company.platform.logging.domain.model;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
public final class MaskingRule {
    private final String name;
    private final boolean mandatory;
    private final MaskingMatchType matchType;
    private final List<String> expressions;
    private final PiiType piiType;
    private final MaskingType maskingType;
    private final int visiblePrefix;
    private final int visibleSuffix;
    private final String substitution;
    private final boolean preserveDomain;
    private final String strategyBean;

    @Builder
    public MaskingRule(
        String name, boolean mandatory, MaskingMatchType matchType,
        List<String> expressions, PiiType piiType, MaskingType maskingType,
        int visiblePrefix, int visibleSuffix, String substitution,
        boolean preserveDomain, String strategyBean
    ) {
        this.name = name;
        this.mandatory = mandatory;
        this.matchType = matchType;
        this.expressions = List.copyOf(expressions == null ? List.of() : expressions);
        this.piiType = piiType == null ? PiiType.GENERIC : piiType;
        this.maskingType = maskingType == null ? MaskingType.SUBSTITUTION : maskingType;
        this.visiblePrefix = visiblePrefix;
        this.visibleSuffix = visibleSuffix;
        this.substitution = substitution == null ? "***" : substitution;
        this.preserveDomain = preserveDomain;
        this.strategyBean = strategyBean;
    }
}
