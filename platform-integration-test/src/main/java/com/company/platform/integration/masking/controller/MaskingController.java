package com.company.platform.integration.masking.controller;

import com.company.platform.core.rest.response.ApiResponse;
import com.company.platform.integration.masking.dto.request.MaskingRequest;
import com.company.platform.integration.masking.dto.response.MaskingResponse;
import com.company.platform.integration.masking.service.MaskingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/masking")
@RequiredArgsConstructor
@Validated
public class MaskingController {
    private final MaskingService maskingService;

    @PostMapping("/raw")
    public ApiResponse<MaskingRequest> maskRawRequest(@RequestBody @Valid MaskingRequest request) {
        return ApiResponse.success(maskingService.maskRawRequest(request));
    }

    @PostMapping("/masking-anotation")
    public ApiResponse<MaskingResponse> maskingAnotation(@RequestBody @Valid MaskingRequest request) {
        return ApiResponse.success(maskingService.maskingAnotation(request));
    }

    @PostMapping("/masking-use-bean")
    public ApiResponse<MaskingResponse> maskingUseBean(@RequestBody @Valid MaskingRequest request) {
        return ApiResponse.success(maskingService.maskingUseBean(request));
    }
}
