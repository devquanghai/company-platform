package com.company.platform.cache.application.resolver;

import com.company.platform.cache.autoconfigure.properties.CacheStoreProperties;
import com.company.platform.cache.autoconfigure.properties.NamedCacheProperties;
import com.company.platform.cache.autoconfigure.properties.PlatformCacheProperties;
import com.company.platform.cache.domain.exception.PlatformCacheConfigurationException;
import com.company.platform.cache.domain.model.CacheProviderType;
import com.company.platform.cache.support.PlatformCachePropertiesValidator;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class CacheDefinitionRegistry {
    private final Map<String, CacheStoreDefinition> stores;
    private final Map<String, NamedCacheDefinition> caches;

    public CacheDefinitionRegistry(
        PlatformCacheProperties properties,
        PlatformCachePropertiesValidator validator
    ) {
        Objects.requireNonNull(properties, "properties must not be null");
        Objects.requireNonNull(validator, "validator must not be null").validate();
        stores = buildStores(properties);
        caches = buildCaches(properties, stores);
    }

    public CacheStoreDefinition requireStore(String name) {
        CacheStoreDefinition definition = stores.get(normalizeLookup(name, "store"));
        if (definition == null) {
            throw configurationFailure("Unknown or disabled cache store: " + safeName(name));
        }
        return definition;
    }

    public NamedCacheDefinition requireCache(String name) {
        NamedCacheDefinition definition = caches.get(normalizeLookup(name, "cache"));
        if (definition == null) {
            throw configurationFailure("Unknown or disabled named cache: " + safeName(name));
        }
        return definition;
    }

    public Map<String, CacheStoreDefinition> getStores() {
        return stores;
    }

    public Map<String, NamedCacheDefinition> getCaches() {
        return caches;
    }

    private Map<String, CacheStoreDefinition> buildStores(PlatformCacheProperties properties) {
        Map<String, CacheStoreDefinition> resolved = new LinkedHashMap<>();
        properties.getStores().forEach((configuredName, configuredStore) -> {
            if (configuredStore != null && configuredStore.isEnabled()) {
                String name = normalizeConfiguredName(configuredName, "store");
                CacheStoreDefinition previous = resolved.put(
                    name,
                    new CacheStoreDefinition(name, configuredStore.getProvider(), configuredStore)
                );
                if (previous != null) {
                    throw configurationFailure("Duplicate normalized cache store: " + name);
                }
            }
        });
        return Collections.unmodifiableMap(resolved);
    }

    private Map<String, NamedCacheDefinition> buildCaches(
        PlatformCacheProperties platform,
        Map<String, CacheStoreDefinition> resolvedStores
    ) {
        Map<String, NamedCacheDefinition> resolved = new LinkedHashMap<>();
        platform.getCaches().forEach((configuredName, configuredCache) -> {
            if (configuredCache != null && configuredCache.isEnabled()) {
                String name = normalizeConfiguredName(configuredName, "cache");
                NamedCacheDefinition definition = resolveCache(
                    name, configuredCache, platform, resolvedStores);
                if (resolved.put(name, definition) != null) {
                    throw configurationFailure("Duplicate normalized named cache: " + name);
                }
            }
        });
        return Collections.unmodifiableMap(resolved);
    }

    private NamedCacheDefinition resolveCache(
        String name,
        NamedCacheProperties cache,
        PlatformCacheProperties platform,
        Map<String, CacheStoreDefinition> resolvedStores
    ) {
        Duration ttl = cache.getTtl() == null
            ? platform.getDefaults().getTtl() : cache.getTtl();
        boolean cacheNullValues = cache.isCacheNullValues()
            || platform.getDefaults().isCacheNullValues();
        String configuredPrefix = cache.getKey().getPrefix();
        String prefix = configuredPrefix == null || configuredPrefix.isBlank()
            ? platform.getDefaults().getKeyPrefix() : configuredPrefix.trim();

        if (cache.getMultiLevel().isEnabled()) {
            CacheStoreDefinition l1 = requireResolvedStore(
                resolvedStores, cache.getMultiLevel().getL1Store(), name, "L1");
            CacheStoreDefinition l2 = requireResolvedStore(
                resolvedStores, cache.getMultiLevel().getL2Store(), name, "L2");
            return new NamedCacheDefinition(
                name, CacheProviderType.MULTI_LEVEL, null, l1, l2, ttl,
                cacheNullValues, platform.getDefaults().getMaximumEntrySize(), prefix, cache);
        }

        CacheStoreDefinition primary = requireResolvedStore(
            resolvedStores, cache.getStore(), name, "primary");
        return new NamedCacheDefinition(
            name, primary.getProvider(), primary, null, null, ttl,
            cacheNullValues, platform.getDefaults().getMaximumEntrySize(), prefix, cache);
    }

    private CacheStoreDefinition requireResolvedStore(
        Map<String, CacheStoreDefinition> resolvedStores,
        String configuredName,
        String cacheName,
        String role
    ) {
        CacheStoreDefinition store = resolvedStores.get(normalizeLookup(configuredName, "store"));
        if (store == null) {
            throw configurationFailure(
                "Named cache " + cacheName + " references unknown " + role + " store");
        }
        return store;
    }

    private String normalizeConfiguredName(String name, String kind) {
        String normalized = normalizeLookup(name, kind);
        if (!normalized.equals(name)) {
            throw configurationFailure(
                "Configured " + kind + " name must already be canonical: " + safeName(name));
        }
        return normalized;
    }

    private String normalizeLookup(String name, String kind) {
        if (name == null || name.isBlank()) {
            throw configurationFailure(kind + " name must not be blank");
        }
        return name.trim().toLowerCase(Locale.ROOT);
    }

    private String safeName(String name) {
        return name == null ? "<null>" : name.replaceAll("[^a-zA-Z0-9._-]", "?");
    }

    private PlatformCacheConfigurationException configurationFailure(String message) {
        return new PlatformCacheConfigurationException(message);
    }
}
