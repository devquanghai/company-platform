package com.company.platform.logging.api.masking;

import com.company.platform.logging.domain.model.MaskingType;

import java.util.Optional;

public interface MaskingStrategyRegistry {
    Optional<MaskingStrategy> find(MaskingType type);
    Optional<MaskingStrategy> find(String beanName);
}
