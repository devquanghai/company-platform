package com.company.platform.integration.cacheapp.api;

import java.util.List;

public record CacheMutationResponse(
    String key,
    String action,
    List<String> tiers
) {
    public CacheMutationResponse {
        tiers = List.copyOf(tiers);
    }
}
