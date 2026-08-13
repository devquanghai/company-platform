package com.company.platform.cache.api.lock;

import java.util.Optional;
import java.util.function.Supplier;

/** Extension contract; the consuming application must provide a distributed adapter. */
public interface DistributedLockOperations {
    <T> T executeWithLock(
        String lockName, LockOptions options, Supplier<T> action);

    Optional<LockHandle> tryLock(String lockName, LockOptions options);
}
