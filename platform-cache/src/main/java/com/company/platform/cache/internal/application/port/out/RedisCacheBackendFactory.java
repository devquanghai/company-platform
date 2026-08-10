package com.company.platform.cache.internal.application.port.out;

public interface RedisCacheBackendFactory {
    CacheBackend create(String storeName, String cacheName);
}
