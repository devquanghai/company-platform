package com.company.platform.integration.cacheapp.api;

public record CacheClearResponse(
    String cache,
    boolean success,
    String strategy,
    Long deletedCount
) {
}
