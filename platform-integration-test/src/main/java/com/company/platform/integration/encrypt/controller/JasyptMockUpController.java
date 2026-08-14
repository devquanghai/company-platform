package com.company.platform.integration.encrypt.controller;

import com.company.platform.logging.api.crypto.PropertyCryptoService;
import com.company.platform.core.rest.response.ApiResponse;
import com.company.platform.integration.encrypt.dto.request.CryptoValueRequest;
import com.company.platform.integration.encrypt.dto.response.CryptoValueResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/encrypt/jasypt-mock")
@RequiredArgsConstructor
public class JasyptMockUpController {
    private final PropertyCryptoService service;

    @PostMapping("/bean/encrypt")
    public ApiResponse<CryptoValueResponse> encrypt(@RequestBody @Valid CryptoValueRequest request) {
        return response(service.encrypt(request.getValue()));
    }

    @PostMapping("/bean/decrypt")
    public ApiResponse<CryptoValueResponse> decrypt(@RequestBody @Valid CryptoValueRequest request) {
        return response(service.decrypt(request.getValue()));
    }
//
//    @PostMapping("/annotation/encrypt")
//    public ApiResponse<CryptoValueResponse> encryptAnnotated(
//        @RequestBody @Valid CryptoValueRequest request
//    ) {
//        return response(service.encryptAnnotated(request.getValue()));
//    }
//
//    @PostMapping("/annotation/decrypt")
//    public ApiResponse<CryptoValueResponse> decryptAnnotated(
//        @RequestBody @Valid CryptoValueRequest request
//    ) {
//        return response(service.decryptAnnotated(request.getValue()));
//    }

    private static ApiResponse<CryptoValueResponse> response(String value) {
        return ApiResponse.success(new CryptoValueResponse(value));
    }
}
