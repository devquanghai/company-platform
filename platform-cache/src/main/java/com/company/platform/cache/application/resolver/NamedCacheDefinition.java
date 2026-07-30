package com.company.platform.cache.application.resolver;

import com.company.platform.cache.autoconfigure.properties.NamedCacheProperties;
import com.company.platform.cache.domain.model.CacheProviderType;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

import java.time.Duration;
import java.util.Objects;

@Getter
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public final class NamedCacheDefinition {
    String name;
    CacheProviderType provider;
    CacheStoreDefinition primaryStore;
    CacheStoreDefinition l1Store;
    CacheStoreDefinition l2Store;
    Duration ttl;
    boolean cacheNullValues;
    long maximumEntrySize;
    String keyPrefix;
    NamedCacheProperties properties;

    public NamedCacheDefinition(
        String name,
        CacheProviderType provider,
        CacheStoreDefinition primaryStore,
        CacheStoreDefinition l1Store,
        CacheStoreDefinition l2Store,
        Duration ttl,
        boolean cacheNullValues,
        long maximumEntrySize,
        String keyPrefix,
        NamedCacheProperties properties
    ) {
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.provider = Objects.requireNonNull(provider, "provider must not be null");
        this.primaryStore = primaryStore;
        this.l1Store = l1Store;
        this.l2Store = l2Store;
        this.ttl = Objects.requireNonNull(ttl, "ttl must not be null");
        this.cacheNullValues = cacheNullValues;
        if (maximumEntrySize < 1) {
            throw new IllegalArgumentException("maximumEntrySize must be positive");
        }
        this.maximumEntrySize = maximumEntrySize;
        this.keyPrefix = Objects.requireNonNull(keyPrefix, "keyPrefix must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }

    public boolean isMultiLevel() {
        return provider == CacheProviderType.MULTI_LEVEL;
    }
}
