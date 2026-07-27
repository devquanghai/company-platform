package com.company.platform.logging.masking.strategy;

import com.company.platform.logging.api.masking.MaskingStrategy;
import com.company.platform.logging.domain.model.MaskingContext;
import com.company.platform.logging.domain.model.MaskingResult;
import com.company.platform.logging.domain.model.MaskingType;

public final class PartialMaskingStrategy implements MaskingStrategy {
    @Override public MaskingType type() { return MaskingType.PARTIAL; }

    @Override
    public MaskingResult mask(String value, MaskingContext context) {
        if (value == null || value.isEmpty()) {
            return MaskingResult.unchanged(value);
        }
        if (context.isPreserveDomain() && value.indexOf('@') > 0) {
            int at = value.indexOf('@');
            String local = partial(value.substring(0, at), context);
            return MaskingResult.masked(local + value.substring(at));
        }
        return MaskingResult.masked(partial(value, context));
    }

    private static String partial(String value, MaskingContext context) {
        int prefix = Math.min(context.getVisiblePrefix(), value.length());
        int suffix = Math.min(context.getVisibleSuffix(), value.length() - prefix);
        int hidden = value.length() - prefix - suffix;
        if (hidden <= 0) {
            return "***";
        }
        return value.substring(0, prefix) + "*".repeat(hidden)
            + value.substring(value.length() - suffix);
    }
}
