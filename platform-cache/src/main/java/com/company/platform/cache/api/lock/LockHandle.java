package com.company.platform.cache.api.lock;

public interface LockHandle extends AutoCloseable {
    String getLockName();

    boolean isOwned();

    @Override
    void close();
}
