package com.company.platform.integration.client.controller;

import com.company.platform.integration.client.dto.request.ClientResourcePatchRequest;
import com.company.platform.integration.client.dto.request.ClientResourceRequest;
import com.company.platform.integration.client.dto.response.ClientResourceResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
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

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/service-exchange/downstream/resources")
public class ServiceExchangeDownstreamController {
    @GetMapping("/{id}")
    ClientResourceResponse get(
        @PathVariable String id,
        @RequestParam(defaultValue = "false") boolean includeDetails,
        @RequestHeader(name = "X-Request-Source", defaultValue = "unknown")
        String requestSource
    ) {
        return response(id, "GET", "resource-" + id,
            includeDetails ? "details requested" : null,
            includeDetails, requestSource, Map.of());
    }

    @PostMapping
    ResponseEntity<ClientResourceResponse> create(
        @RequestBody @Valid ClientResourceRequest request,
        @RequestHeader(name = "X-Request-Source", defaultValue = "unknown")
        String requestSource
    ) {
        return ResponseEntity.status(201).body(response(
            UUID.randomUUID().toString(), "POST", request.name(), request.description(),
            true, requestSource, request.attributes()));
    }

    @PutMapping("/{id}")
    ClientResourceResponse replace(
        @PathVariable String id,
        @RequestBody @Valid ClientResourceRequest request,
        @RequestHeader(name = "X-Request-Source", defaultValue = "unknown")
        String requestSource
    ) {
        return response(id, "PUT", request.name(), request.description(),
            true, requestSource, request.attributes());
    }

    @PatchMapping("/{id}")
    ClientResourceResponse update(
        @PathVariable String id,
        @RequestBody @Valid ClientResourcePatchRequest request,
        @RequestHeader(name = "X-Request-Source", defaultValue = "unknown")
        String requestSource
    ) {
        return response(id, "PATCH", request.name(), request.description(),
            true, requestSource, request.attributes());
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@PathVariable String id) {
        return ResponseEntity.noContent().build();
    }

    private ClientResourceResponse response(
        String id, String operation, String name, String description,
        boolean detailsIncluded, String requestSource, Map<String, Object> attributes
    ) {
        return new ClientResourceResponse(
            id, operation, name, description, detailsIncluded, requestSource, attributes);
    }
}
