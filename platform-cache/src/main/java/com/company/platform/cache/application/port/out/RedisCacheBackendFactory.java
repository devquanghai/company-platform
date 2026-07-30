package com.company.platform.cache.application.port.out;

public interface RedisCacheBackendFactory {
    CacheBackend create(String storeName, String cacheName);
}
