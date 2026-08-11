package com.company.platform.integration.cache.dto.response;

import com.company.platform.integration.cache.dto.request.CacheRequest;

public record VersionedCacheResponse(long version, CacheRequest value) {
}
