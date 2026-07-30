package com.company.platform.cache.domain.result;

public enum OptimisticUpdateStatus {
    UPDATED,
    VERSION_CONFLICT,
    NOT_FOUND,
    FAILED
}
