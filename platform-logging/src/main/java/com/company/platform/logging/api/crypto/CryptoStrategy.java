package com.company.platform.logging.api.crypto;

import com.company.platform.logging.domain.model.CryptoAlgorithm;
import com.company.platform.logging.domain.model.CryptoContext;
import com.company.platform.logging.domain.model.CryptoProviderType;
import com.company.platform.logging.domain.model.CryptoResult;

public interface CryptoStrategy {
    CryptoProviderType provider();
    CryptoAlgorithm algorithm();
    byte[] encrypt(byte[] plaintext, CryptoContext context);
    byte[] decrypt(byte[] ciphertext, CryptoContext context);

    default CryptoResult encryptResult(byte[] plaintext, CryptoContext context) {
        return CryptoResult.builder().mode("DIRECT")
            .ciphertext(encrypt(plaintext, context)).build();
    }
}
