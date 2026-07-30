package com.company.platform.cache.domain.model;

public enum CacheResultStatus {
    HIT,
    MISS,
    HIT_FALLBACK,
    HIT_STALE,
    LOADED,
    REJECTED,
    FAILED
}
