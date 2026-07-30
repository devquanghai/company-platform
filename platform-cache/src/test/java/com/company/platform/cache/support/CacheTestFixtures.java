package com.company.platform.cache.support;

import com.company.platform.cache.autoconfigure.properties.CacheStoreProperties;
import com.company.platform.cache.autoconfigure.properties.NamedCacheProperties;
import com.company.platform.cache.autoconfigure.properties.PlatformCacheProperties;
import com.company.platform.cache.domain.model.CacheProviderType;
import com.company.platform.cache.domain.policy.CacheFailurePolicy;

public final class CacheTestFixtures {
    public static final String STORE = "local";
    public static final String CACHE = "users";

    private CacheTestFixtures() {
    }

    public static PlatformCacheProperties validProperties() {
        PlatformCacheProperties properties = new PlatformCacheProperties();
        CacheStoreProperties store = new CacheStoreProperties();
        store.setProvider(CacheProviderType.CAFFEINE);
        properties.getStores().put(STORE, store);

        NamedCacheProperties cache = new NamedCacheProperties();
        cache.setStore(STORE);
        cache.setFailurePolicy(CacheFailurePolicy.FAIL_OPEN);
        properties.getCaches().put(CACHE, cache);
        return properties;
    }
}
