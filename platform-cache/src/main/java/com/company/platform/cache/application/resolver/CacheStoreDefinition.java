package com.company.platform.cache.application.resolver;

import com.company.platform.cache.autoconfigure.properties.CacheStoreProperties;
import com.company.platform.cache.domain.model.CacheProviderType;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

import java.util.Objects;

@Getter
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public final class CacheStoreDefinition {
    String name;
    CacheProviderType provider;
    CacheStoreProperties properties;

    public CacheStoreDefinition(
        String name,
        CacheProviderType provider,
        CacheStoreProperties properties
    ) {
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.provider = Objects.requireNonNull(provider, "provider must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }
}
