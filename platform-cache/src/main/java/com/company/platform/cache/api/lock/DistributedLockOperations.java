package com.company.platform.cache.api.lock;

import java.util.Optional;
import java.util.function.Supplier;

public interface DistributedLockOperations {
    <T> T executeWithLock(
        String lockName, LockOptions options, Supplier<T> action);

    Optional<LockHandle> tryLock(String lockName, LockOptions options);
}
