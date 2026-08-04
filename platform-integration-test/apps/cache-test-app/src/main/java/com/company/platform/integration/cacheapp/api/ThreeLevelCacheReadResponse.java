package com.company.platform.integration.cacheapp.api;

import java.util.List;

public record ThreeLevelCacheReadResponse(
    String key,
    boolean hit,
    String value,
    String source,
    List<String> promotedTo
) {
    public ThreeLevelCacheReadResponse {
        promotedTo = List.copyOf(promotedTo);
    }
}
