package com.company.platform.logging.masking.strategy;

import com.company.platform.logging.api.masking.MaskingStrategy;
import com.company.platform.logging.domain.model.MaskingContext;
import com.company.platform.logging.domain.model.MaskingResult;
import com.company.platform.logging.domain.model.MaskingType;

public final class RemoveMaskingStrategy implements MaskingStrategy {
    @Override public MaskingType type() { return MaskingType.REMOVE; }
    @Override public MaskingResult mask(String value, MaskingContext context) {
        return MaskingResult.removed();
    }
}
