package com.company.platform.integration.encrypt.service;

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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class RsaService {
    public RsaEncryptResponse encrypt(@Valid RsaEncryptRequest request) {

    }

    public RsaDecryptResponse decrypt(@Valid RsaDecryptRequest request) {
    }

    public RsaSignResponse sign(@Valid RsaSignRequest request) {
    }

    public RsaVerifyResponse verify(@Valid RsaVerifyRequest request) {
    }
}
