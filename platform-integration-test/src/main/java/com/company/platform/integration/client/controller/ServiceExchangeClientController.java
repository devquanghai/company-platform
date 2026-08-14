package com.company.platform.integration.client.controller;

import com.company.platform.core.rest.response.ApiResponse;
import com.company.platform.integration.client.dto.request.ClientResourcePatchRequest;
import com.company.platform.integration.client.dto.request.ClientResourceRequest;
import com.company.platform.integration.client.dto.response.ClientCallResponse;
import com.company.platform.integration.client.dto.response.ClientResourceResponse;
import com.company.platform.integration.client.service.ServiceExchangeDemoService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/service-exchange/client/resources")
@RequiredArgsConstructor
@Validated
public class ServiceExchangeClientController {
    private final ServiceExchangeDemoService service;

    @GetMapping("/{id}")
    public ApiResponse<ClientCallResponse<ClientResourceResponse>> get(
        @PathVariable @Pattern(regexp = "[A-Za-z0-9_-]{1,64}") String id,
        @RequestParam(defaultValue = "false") boolean includeDetails,
        @RequestHeader(name = "X-Request-Source", defaultValue = "integration-api")
        @Pattern(regexp = "[A-Za-z0-9._-]{1,64}")
        String requestSource
    ) {
        return ApiResponse.success(service.get(id, includeDetails, requestSource));
    }

    @PostMapping
    public ApiResponse<ClientCallResponse<ClientResourceResponse>> create(
        @RequestBody @Valid ClientResourceRequest request,
        @RequestHeader(name = "X-Request-Source", defaultValue = "integration-api")
        @Pattern(regexp = "[A-Za-z0-9._-]{1,64}")
        String requestSource
    ) {
        return ApiResponse.success(service.create(request, requestSource));
    }

    @PutMapping("/{id}")
    public ApiResponse<ClientCallResponse<ClientResourceResponse>> replace(
        @PathVariable @Pattern(regexp = "[A-Za-z0-9_-]{1,64}") String id,
        @RequestBody @Valid ClientResourceRequest request,
        @RequestHeader(name = "X-Request-Source", defaultValue = "integration-api")
        @Pattern(regexp = "[A-Za-z0-9._-]{1,64}")
        String requestSource
    ) {
        return ApiResponse.success(service.replace(id, request, requestSource));
    }

    @PatchMapping("/{id}")
    public ApiResponse<ClientCallResponse<ClientResourceResponse>> update(
        @PathVariable @Pattern(regexp = "[A-Za-z0-9_-]{1,64}") String id,
        @RequestBody @Valid ClientResourcePatchRequest request,
        @RequestHeader(name = "X-Request-Source", defaultValue = "integration-api")
        @Pattern(regexp = "[A-Za-z0-9._-]{1,64}")
        String requestSource
    ) {
        return ApiResponse.success(service.update(id, request, requestSource));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<ClientCallResponse<Void>> delete(
        @PathVariable @Pattern(regexp = "[A-Za-z0-9_-]{1,64}") String id,
        @RequestHeader(name = "X-Request-Source", defaultValue = "integration-api")
        @Pattern(regexp = "[A-Za-z0-9._-]{1,64}")
        String requestSource
    ) {
        return ApiResponse.success(service.delete(id, requestSource));
    }
}
