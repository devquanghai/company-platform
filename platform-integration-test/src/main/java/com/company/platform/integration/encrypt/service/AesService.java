package com.company.platform.integration.encrypt.service;

import com.company.platform.logging.annotation.crypto.DecryptValue;
import com.company.platform.logging.annotation.crypto.EncryptResult;
import com.company.platform.logging.api.crypto.CryptoService;
import com.company.platform.logging.domain.model.CryptoAlgorithm;
import com.company.platform.logging.domain.model.CryptoRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AesService {
    public static final String KEY_ALIAS = "integration-data-hihi";
    private static final CryptoRequest REQUEST = CryptoRequest.builder()
        .algorithm(CryptoAlgorithm.AES_GCM_256).keyAlias(KEY_ALIAS).build();

    private final CryptoService crypto;

    public String encrypt(String value) {
        return crypto.encrypt(value, REQUEST);
    }

    public String decrypt(String value) {
        return crypto.decrypt(value, REQUEST);
    }

    @EncryptResult(algorithm = CryptoAlgorithm.AES_GCM_256, keyAlias = KEY_ALIAS)
    public String encryptAnnotated(String value) {
        return value;
    }

    public String decryptAnnotated(
        @DecryptValue(algorithm = CryptoAlgorithm.AES_GCM_256, keyAlias = KEY_ALIAS)
        String value
    ) {
        return value;
    }
}
