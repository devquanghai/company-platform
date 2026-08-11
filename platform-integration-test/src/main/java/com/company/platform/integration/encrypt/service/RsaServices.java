package com.company.platform.integration.encrypt.service;

import com.company.platform.core.crypto.rsa.RsaService;
import com.company.platform.core.json.JsonMapperHelper;
import com.company.platform.integration.encrypt.dto.request.rsa.RsaDecryptRequest;
import com.company.platform.integration.encrypt.dto.request.rsa.RsaEncryptRequest;
import com.company.platform.integration.encrypt.dto.request.rsa.RsaSignRequest;
import com.company.platform.integration.encrypt.dto.request.rsa.RsaVerifyRequest;
import com.company.platform.integration.encrypt.dto.response.RsaDecryptResponse;
import com.company.platform.integration.encrypt.dto.response.RsaEncryptResponse;
import com.company.platform.integration.encrypt.dto.response.RsaSignResponse;
import com.company.platform.integration.encrypt.dto.response.RsaVerifyResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.security.KeyPair;

@Service
@RequiredArgsConstructor
public class RsaServices {
    private final RsaService rsa;
    private final JsonMapperHelper json;
    private final KeyPair keyPair;

    public RsaEncryptResponse encrypt(@Valid RsaEncryptRequest request) {
        return new RsaEncryptResponse(rsa.encryptMixRsaAes(
            json.toJson(request.getData()), keyPair.getPublic()));
    }

    public RsaDecryptResponse decrypt(@Valid RsaDecryptRequest request) {
        return new RsaDecryptResponse(rsa.decryptMixRsaAes(
            request.getEncryptedData(), keyPair.getPrivate()));
    }

    public RsaSignResponse sign(@Valid RsaSignRequest request) {
        return new RsaSignResponse(rsa.sign(
            request.getData(), keyPair.getPrivate()));
    }

    public RsaVerifyResponse verify(@Valid RsaVerifyRequest request) {
        return new RsaVerifyResponse(rsa.verify(
            request.getData(), request.getSignature(), keyPair.getPublic()));
    }
}
