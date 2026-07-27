package com.company.platform.logging.masking.strategy;

import com.company.platform.logging.api.masking.MaskingStrategy;
import com.company.platform.logging.domain.model.MaskingContext;
import com.company.platform.logging.domain.model.MaskingResult;
import com.company.platform.logging.domain.model.MaskingType;

public final class SubstitutionMaskingStrategy implements MaskingStrategy {
    @Override public MaskingType type() { return MaskingType.SUBSTITUTION; }
    @Override public MaskingResult mask(String value, MaskingContext context) {
        return value == null ? MaskingResult.unchanged(null)
            : MaskingResult.masked(context.getSubstitution());
    }
}
