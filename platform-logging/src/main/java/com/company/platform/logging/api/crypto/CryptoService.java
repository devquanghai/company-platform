package com.company.platform.logging.api.crypto;

import com.company.platform.logging.domain.model.CryptoRequest;

public interface CryptoService {
    String encrypt(String plaintext, CryptoRequest request);
    String decrypt(String ciphertext, CryptoRequest request);
    byte[] encrypt(byte[] plaintext, CryptoRequest request);
    byte[] decrypt(byte[] ciphertext, CryptoRequest request);
}
