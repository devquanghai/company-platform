package com.company.platform.cache.application.resolver;

import com.company.platform.cache.application.port.out.CacheBackend;
import com.company.platform.cache.application.port.out.CacheBackendRegistry;
import com.company.platform.cache.domain.exception.PlatformCacheConfigurationException;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class DefaultCacheBackendRegistry implements CacheBackendRegistry {
    private final Map<String, CacheBackend> backends;

    public DefaultCacheBackendRegistry(Map<String, CacheBackend> backends) {
        Objects.requireNonNull(backends, "backends must not be null");
        Map<String, CacheBackend> copy = new LinkedHashMap<>();
        backends.forEach((name, backend) -> copy.put(
            requireName(name), Objects.requireNonNull(backend, "backend must not be null")));
        this.backends = Collections.unmodifiableMap(copy);
    }

    @Override
    public CacheBackend require(String cacheName) {
        CacheBackend backend = backends.get(requireName(cacheName));
        if (backend == null) {
            throw new PlatformCacheConfigurationException(
                "Unknown or disabled named cache: " + safeName(cacheName));
        }
        return backend;
    }

    public Map<String, CacheBackend> getBackends() {
        return backends;
    }

    @Override
    public Map<String, CacheBackend> snapshot() {
        return backends;
    }

    private String requireName(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("cacheName must not be blank");
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String safeName(String value) {
        return value == null ? "<null>" : value.replaceAll("[^a-zA-Z0-9._-]", "?");
    }
}
