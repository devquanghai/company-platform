package com.company.platform.integration.encrypt.controller;

import com.company.platform.core.rest.response.ApiResponse;
import com.company.platform.integration.encrypt.dto.request.rsa.RsaDecryptRequest;
import com.company.platform.integration.encrypt.dto.request.rsa.RsaEncryptRequest;
import com.company.platform.integration.encrypt.dto.request.rsa.RsaSignRequest;
import com.company.platform.integration.encrypt.dto.request.rsa.RsaVerifyRequest;
import com.company.platform.integration.encrypt.dto.response.RsaDecryptResponse;
import com.company.platform.integration.encrypt.dto.response.RsaEncryptResponse;
import com.company.platform.integration.encrypt.dto.response.RsaSignResponse;
import com.company.platform.integration.encrypt.dto.response.RsaVerifyResponse;
import com.company.platform.integration.encrypt.service.RsaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/rsa")
@RequiredArgsConstructor
@Validated
public class RsaController {
    private final RsaService rsaService;

    @PostMapping("/encrypt")
    public ApiResponse<RsaEncryptResponse> encrypt(@RequestBody @Valid RsaEncryptRequest request) {
        return ApiResponse.success(rsaService.encrypt(request));
    }

    @PostMapping("/decrypt")
    public ApiResponse<RsaDecryptResponse> decrypt(@RequestBody @Valid RsaDecryptRequest request) {
        return ApiResponse.success(rsaService.decrypt(request));
    }

    @PostMapping("/sign")
    public ApiResponse<RsaSignResponse> sign(@RequestBody @Valid RsaSignRequest request) {
        return ApiResponse.success(rsaService.sign(request));
    }

    @PostMapping("/verify")
    public ApiResponse<RsaVerifyResponse> verify(@RequestBody @Valid RsaVerifyRequest request) {
        return ApiResponse.success(rsaService.verify(request));
    }
}
