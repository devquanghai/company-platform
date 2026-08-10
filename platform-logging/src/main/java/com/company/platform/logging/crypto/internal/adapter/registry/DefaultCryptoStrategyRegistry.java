package com.company.platform.logging.crypto.internal.adapter.registry;

import com.company.platform.logging.api.crypto.CryptoStrategy;
import com.company.platform.logging.api.crypto.CryptoStrategyRegistry;
import com.company.platform.logging.domain.exception.PlatformCryptoException;
import com.company.platform.logging.domain.model.CryptoAlgorithm;
import com.company.platform.logging.domain.model.CryptoProviderType;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class DefaultCryptoStrategyRegistry implements CryptoStrategyRegistry {
    private final Map<String, CryptoStrategy> names;
    private final Map<String, CryptoStrategy> algorithms;

    public DefaultCryptoStrategyRegistry(Map<String, CryptoStrategy> strategies) {
        this.names = Map.copyOf(strategies);
        LinkedHashMap<String, CryptoStrategy> values = new LinkedHashMap<>();
        strategies.forEach((name, strategy) ->
            values.putIfAbsent(key(strategy.provider(), strategy.algorithm()), strategy));
        this.algorithms = Map.copyOf(values);
    }

    @Override
    public CryptoStrategy resolve(
        CryptoProviderType provider, CryptoAlgorithm algorithm
    ) {
        CryptoStrategy strategy = algorithms.get(key(provider, algorithm));
        if (strategy == null) {
            throw new PlatformCryptoException(
                "PLATFORM.CRYPTO.STRATEGY_UNAVAILABLE",
                "Requested crypto provider/algorithm is unavailable");
        }
        return strategy;
    }

    @Override public Optional<CryptoStrategy> find(String beanName) {
        return Optional.ofNullable(names.get(beanName));
    }

    private static String key(CryptoProviderType provider, CryptoAlgorithm algorithm) {
        return provider.name() + ":" + algorithm.name();
    }
}
