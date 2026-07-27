package com.company.platform.logging.api.crypto;

public interface CryptoObjectProcessor {
    <T> T encryptAnnotatedFields(T source);
    <T> T decryptAnnotatedFields(T source);
}
