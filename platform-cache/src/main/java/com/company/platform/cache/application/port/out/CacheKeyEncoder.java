package com.company.platform.cache.application.port.out;

import com.company.platform.cache.application.resolver.NamedCacheDefinition;

public interface CacheKeyEncoder {
    String encode(NamedCacheDefinition cache, Object key, String namespaceToken);
}
