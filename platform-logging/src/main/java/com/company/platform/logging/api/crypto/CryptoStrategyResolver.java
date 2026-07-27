package com.company.platform.logging.api.crypto;

import com.company.platform.logging.domain.model.CryptoAlgorithm;
import com.company.platform.logging.domain.model.CryptoProviderType;

public interface CryptoStrategyResolver {
    CryptoStrategy resolve(CryptoProviderType provider, CryptoAlgorithm algorithm);
}
