package com.company.platform.logging.crypto.factory;

import com.company.platform.logging.api.crypto.CryptoProviderFactory;
import com.company.platform.logging.api.crypto.CryptoStrategy;
import com.company.platform.logging.crypto.envelope.CipherEnvelopeCodec;
import com.company.platform.logging.crypto.key.KeyResolver;
import com.company.platform.logging.domain.exception.PlatformCryptoException;
import com.company.platform.logging.domain.model.CryptoAlgorithm;
import com.company.platform.logging.domain.model.CryptoProviderType;

import java.util.Map;

public final class JcaCryptoProviderFactory implements CryptoProviderFactory {
    private final Map<CryptoAlgorithm, CryptoStrategy> strategies;
    private final KeyResolver keys;
    private final CipherEnvelopeCodec envelopes;

    public JcaCryptoProviderFactory(
        Map<CryptoAlgorithm, CryptoStrategy> strategies,
        KeyResolver keys, CipherEnvelopeCodec envelopes
    ) {
        this.strategies = Map.copyOf(strategies);
        this.keys = keys;
        this.envelopes = envelopes;
    }

    @Override public CryptoProviderType providerType() { return CryptoProviderType.JCA; }
    @Override public CryptoStrategy createStrategy(CryptoAlgorithm algorithm) {
        CryptoStrategy strategy = strategies.get(algorithm);
        if (strategy == null) {
            throw new PlatformCryptoException(
                "PLATFORM.CRYPTO.ALGORITHM_UNAVAILABLE",
                "JCA algorithm is unavailable");
        }
        return strategy;
    }
    @Override public KeyResolver createKeyResolver() { return keys; }
    @Override public CipherEnvelopeCodec createEnvelopeCodec() { return envelopes; }
}
