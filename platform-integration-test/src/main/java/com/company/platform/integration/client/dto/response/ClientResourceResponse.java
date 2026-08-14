package com.company.platform.integration.client.dto.response;

import java.util.Map;

public record ClientResourceResponse(
    String id,
    String operation,
    String name,
    String description,
    boolean detailsIncluded,
    String requestSource,
    Map<String, Object> attributes
) {
    public ClientResourceResponse {
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }
}
