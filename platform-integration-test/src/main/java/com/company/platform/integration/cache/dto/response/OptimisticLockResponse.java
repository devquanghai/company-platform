package com.company.platform.integration.cache.dto.response;

public record OptimisticLockResponse(
    String status,
    VersionedCacheResponse value,
    String failureCode,
    boolean retryable
) {
}
