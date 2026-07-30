package com.company.platform.cache.observability.health;

import com.company.platform.cache.application.port.out.CacheBackend;
import com.company.platform.cache.application.port.out.CacheBackendRegistry;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class PlatformCacheHealthIndicator implements HealthIndicator {
    private final CacheBackendRegistry backends;

    public PlatformCacheHealthIndicator(CacheBackendRegistry backends) {
        this.backends = Objects.requireNonNull(backends, "backends");
    }

    @Override
    public Health health() {
        Map<String, Object> details = new LinkedHashMap<>();
        boolean healthy = true;
        for (Map.Entry<String, CacheBackend> entry
            : backends.snapshot().entrySet()) {
            Map<String, Object> cache = new LinkedHashMap<>();
            try {
                entry.getValue().namespaceToken();
                cache.put("status", "UP");
                cache.put("estimatedSize", entry.getValue().estimatedSize());
            } catch (RuntimeException failure) {
                healthy = false;
                cache.put("status", "DOWN");
                cache.put("errorCategory", "INFRASTRUCTURE");
            }
            details.put(entry.getKey(), cache);
        }
        Health.Builder builder = healthy ? Health.up() : Health.down();
        return builder.withDetail("caches", details).build();
    }
}
