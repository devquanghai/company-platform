package com.company.platform.logging.api.crypto;

public interface CryptoRotationService {
    boolean requiresRotation(String ciphertext);
    String rotate(String ciphertext);
}
