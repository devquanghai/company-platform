package com.company.platform.integration.cacheapp.api;

public record CacheReadResponse(
    String cache,
    String key,
    boolean hit,
    String value,
    String status,
    String tier
) {
}
