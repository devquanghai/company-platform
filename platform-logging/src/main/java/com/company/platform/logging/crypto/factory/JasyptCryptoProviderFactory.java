package com.company.platform.logging.crypto.factory;

import com.company.platform.logging.api.crypto.CryptoProviderFactory;
import com.company.platform.logging.api.crypto.CryptoStrategy;
import com.company.platform.logging.crypto.envelope.CipherEnvelopeCodec;
import com.company.platform.logging.crypto.key.KeyResolver;
import com.company.platform.logging.domain.exception.PlatformCryptoException;
import com.company.platform.logging.domain.model.CryptoAlgorithm;
import com.company.platform.logging.domain.model.CryptoProviderType;

public final class JasyptCryptoProviderFactory implements CryptoProviderFactory {
    private final CryptoStrategy strategy;
    private final KeyResolver keys;
    private final CipherEnvelopeCodec envelopes;

    public JasyptCryptoProviderFactory(
        CryptoStrategy strategy, KeyResolver keys, CipherEnvelopeCodec envelopes
    ) {
        this.strategy = strategy;
        this.keys = keys;
        this.envelopes = envelopes;
    }

    @Override public CryptoProviderType providerType() { return CryptoProviderType.JASYPT; }
    @Override public CryptoStrategy createStrategy(CryptoAlgorithm algorithm) {
        if (algorithm != CryptoAlgorithm.PBE) {
            throw new PlatformCryptoException(
                "PLATFORM.CRYPTO.ALGORITHM_UNAVAILABLE",
                "Jasypt provider supports only explicit PBE");
        }
        return strategy;
    }
    @Override public KeyResolver createKeyResolver() { return keys; }
    @Override public CipherEnvelopeCodec createEnvelopeCodec() { return envelopes; }
}
