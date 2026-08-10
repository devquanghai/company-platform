package com.company.platform.cache.internal.application.port.out;

import java.util.Map;

public interface CacheBackendRegistry {
    CacheBackend require(String cacheName);

    default Map<String, CacheBackend> snapshot() {
        return Map.of();
    }
}
