package com.company.platform.integration.client.dto.request;

import jakarta.validation.constraints.Size;

import java.util.Map;

public record ClientResourcePatchRequest(
    @Size(max = 120) String name,
    @Size(max = 500) String description,
    Map<String, Object> attributes
) {
    public ClientResourcePatchRequest {
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }
}
