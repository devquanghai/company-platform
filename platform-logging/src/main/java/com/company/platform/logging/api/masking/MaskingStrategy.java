package com.company.platform.logging.api.masking;

import com.company.platform.logging.domain.model.MaskingContext;
import com.company.platform.logging.domain.model.MaskingResult;
import com.company.platform.logging.domain.model.MaskingType;

public interface MaskingStrategy {
    MaskingType type();
    MaskingResult mask(String value, MaskingContext context);
}
