package com.company.platform.logging.api.crypto;

import com.company.platform.logging.crypto.envelope.CipherEnvelopeCodec;
import com.company.platform.logging.crypto.key.KeyResolver;
import com.company.platform.logging.domain.model.CryptoAlgorithm;
import com.company.platform.logging.domain.model.CryptoProviderType;

public interface CryptoProviderFactory {
    CryptoProviderType providerType();
    CryptoStrategy createStrategy(CryptoAlgorithm algorithm);
    KeyResolver createKeyResolver();
    CipherEnvelopeCodec createEnvelopeCodec();
}
