package com.company.platform.logging.masking.internal.adapter.registry;

import com.company.platform.logging.api.masking.MaskingStrategy;
import com.company.platform.logging.api.masking.MaskingStrategyRegistry;
import com.company.platform.logging.domain.model.MaskingType;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class DefaultMaskingStrategyRegistry implements MaskingStrategyRegistry {
    private final Map<MaskingType, MaskingStrategy> byType;
    private final Map<String, MaskingStrategy> byName;

    public DefaultMaskingStrategyRegistry(Map<String, MaskingStrategy> strategies) {
        LinkedHashMap<String, MaskingStrategy> names = new LinkedHashMap<>(strategies);
        EnumMap<MaskingType, MaskingStrategy> types = new EnumMap<>(MaskingType.class);
        names.forEach((name, strategy) -> types.putIfAbsent(strategy.type(), strategy));
        this.byName = Map.copyOf(names);
        this.byType = Map.copyOf(types);
    }

    @Override public Optional<MaskingStrategy> find(MaskingType type) {
        return Optional.ofNullable(byType.get(type));
    }

    @Override public Optional<MaskingStrategy> find(String beanName) {
        return Optional.ofNullable(byName.get(beanName));
    }
}
