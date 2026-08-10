package com.company.platform.logging.autoconfigure;

import com.company.platform.core.json.JsonMapperHelper;
import com.company.platform.logging.api.masking.DataMaskingService;
import com.company.platform.logging.api.masking.MaskingHashKeyProvider;
import com.company.platform.logging.api.masking.MaskingRuleProvider;
import com.company.platform.logging.api.masking.MaskingStrategy;
import com.company.platform.logging.api.masking.MaskingStrategyRegistry;
import com.company.platform.logging.autoconfigure.properties.PlatformLoggingProperties;
import com.company.platform.logging.masking.internal.adapter.registry.DefaultMaskingStrategyRegistry;
import com.company.platform.logging.masking.internal.application.DefaultDataMaskingService;
import com.company.platform.logging.masking.strategy.FullMaskingStrategy;
import com.company.platform.logging.masking.strategy.HashMaskingStrategy;
import com.company.platform.logging.masking.strategy.PartialMaskingStrategy;
import com.company.platform.logging.masking.strategy.RemoveMaskingStrategy;
import com.company.platform.logging.masking.strategy.SubstitutionMaskingStrategy;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import java.util.Map;

@AutoConfiguration(after = PlatformLoggingAutoConfiguration.class)
public class MaskingAutoConfiguration {
    @Bean("platformFullMaskingStrategy")
    @ConditionalOnMissingBean(name = "platformFullMaskingStrategy")
    MaskingStrategy fullMaskingStrategy() {
        return new FullMaskingStrategy();
    }

    @Bean("platformPartialMaskingStrategy")
    @ConditionalOnMissingBean(name = "platformPartialMaskingStrategy")
    MaskingStrategy partialMaskingStrategy() {
        return new PartialMaskingStrategy();
    }

    @Bean("platformSubstitutionMaskingStrategy")
    @ConditionalOnMissingBean(name = "platformSubstitutionMaskingStrategy")
    MaskingStrategy substitutionMaskingStrategy() {
        return new SubstitutionMaskingStrategy();
    }

    @Bean("platformRemoveMaskingStrategy")
    @ConditionalOnMissingBean(name = "platformRemoveMaskingStrategy")
    MaskingStrategy removeMaskingStrategy() {
        return new RemoveMaskingStrategy();
    }

    @Bean("platformHashMaskingStrategy")
    @ConditionalOnMissingBean(name = "platformHashMaskingStrategy")
    MaskingStrategy hashMaskingStrategy(
        ObjectProvider<MaskingHashKeyProvider> keys,
        PlatformLoggingProperties properties
    ) {
        return new HashMaskingStrategy(keys.getIfAvailable(),
            properties.getMasking().getHmacKeyAlias());
    }

    @Bean
    @ConditionalOnMissingBean
    MaskingStrategyRegistry maskingStrategyRegistry(
        Map<String, MaskingStrategy> strategies
    ) {
        return new DefaultMaskingStrategyRegistry(strategies);
    }

    @Bean
    @ConditionalOnMissingBean
    DataMaskingService dataMaskingService(MaskingStrategyRegistry strategies,
                                          PlatformLoggingProperties properties,
                                          ObjectProvider<MaskingRuleProvider> providers,
                                          JsonMapperHelper jsonMapperHelper
    ) {
        return new DefaultDataMaskingService(strategies,
            properties.getMasking(), providers.orderedStream().toList(), jsonMapperHelper);
    }
}
