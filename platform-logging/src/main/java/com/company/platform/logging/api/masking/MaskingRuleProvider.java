package com.company.platform.logging.api.masking;

import com.company.platform.logging.domain.model.MaskingRule;

import java.util.List;

public interface MaskingRuleProvider {
    List<MaskingRule> rules();
}
