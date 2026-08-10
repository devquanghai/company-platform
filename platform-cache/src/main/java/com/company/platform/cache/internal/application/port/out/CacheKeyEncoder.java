package com.company.platform.cache.internal.application.port.out;

import com.company.platform.cache.internal.application.resolver.NamedCacheDefinition;

public interface CacheKeyEncoder {
    String encode(NamedCacheDefinition cache, Object key, String namespaceToken);
}
