package com.company.platform.integration.encrypt.service;

import com.company.platform.logging.annotation.crypto.DecryptValue;
import com.company.platform.logging.annotation.crypto.EncryptResult;
import com.company.platform.logging.api.crypto.CryptoService;
import com.company.platform.logging.domain.model.CryptoAlgorithm;
import com.company.platform.logging.domain.model.CryptoProviderType;
import com.company.platform.logging.domain.model.CryptoRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class JasyptService {
    public static final String KEY_ALIAS = "integration-data";
    private static final CryptoRequest REQUEST = CryptoRequest.builder()
        .provider(CryptoProviderType.JASYPT).algorithm(CryptoAlgorithm.PBE)
        .keyAlias(KEY_ALIAS).build();

    private final CryptoService crypto;

    public String encrypt(String value) {
        return crypto.encrypt(value, REQUEST);
    }

    public String decrypt(String value) {
        return crypto.decrypt(value, REQUEST);
    }

    @EncryptResult(
        provider = CryptoProviderType.JASYPT,
        algorithm = CryptoAlgorithm.PBE,
        keyAlias = KEY_ALIAS
    )
    public String encryptAnnotated(String value) {
        return value;
    }

    public String decryptAnnotated(
        @DecryptValue(
            provider = CryptoProviderType.JASYPT,
            algorithm = CryptoAlgorithm.PBE,
            keyAlias = KEY_ALIAS
        )
        String value
    ) {
        return value;
    }
}
