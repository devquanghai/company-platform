package com.company.platform.cache.adapter.lock;

import com.company.platform.cache.api.lock.DistributedLockOperations;
import com.company.platform.cache.api.lock.LockHandle;
import com.company.platform.cache.api.lock.LockOptions;
import com.company.platform.cache.domain.exception.PlatformCacheOperationException;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

public final class FailClosedDistributedLockOperations
    implements DistributedLockOperations {

    @Override
    public <T> T executeWithLock(
        String lockName, LockOptions options, Supplier<T> action
    ) {
        require(lockName, options);
        Objects.requireNonNull(action, "action");
        throw unavailable();
    }

    @Override
    public Optional<LockHandle> tryLock(
        String lockName, LockOptions options
    ) {
        require(lockName, options);
        return Optional.empty();
    }

    private void require(String lockName, LockOptions options) {
        if (lockName == null || lockName.isBlank()) {
            throw new IllegalArgumentException("lockName must not be blank");
        }
        Objects.requireNonNull(options, "options");
    }

    private PlatformCacheOperationException unavailable() {
        return new PlatformCacheOperationException(
            "CACHE.LOCK.PROVIDER_UNAVAILABLE",
            "Distributed lock provider is unavailable; protected action was not executed",
            null);
    }
}
