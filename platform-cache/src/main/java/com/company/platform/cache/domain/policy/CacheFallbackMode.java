package com.company.platform.cache.domain.policy;

public enum CacheFallbackMode {
    NONE,
    READ_ONLY,
    READ_THROUGH,
    STALE_IF_ERROR,
    LOCAL_READ_WRITE
}
