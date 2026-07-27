package com.company.platform.logging.api.crypto;

import java.util.Optional;

public interface CryptoStrategyRegistry extends CryptoStrategyResolver {
    Optional<CryptoStrategy> find(String beanName);
}
