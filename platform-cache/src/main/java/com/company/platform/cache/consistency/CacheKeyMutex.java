package com.company.platform.cache.consistency;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

public final class CacheKeyMutex {
    private static final int STRIPES = 256;
    private final ReentrantLock[] locks = new ReentrantLock[STRIPES];

    public CacheKeyMutex() {
        for (int index = 0; index < locks.length; index++) {
            locks[index] = new ReentrantLock();
        }
    }

    public void execute(String encodedKey, Runnable operation) {
        execute(encodedKey, () -> {
            operation.run();
            return null;
        });
    }

    public <T> T execute(String encodedKey, Supplier<T> operation) {
        Objects.requireNonNull(encodedKey, "encodedKey");
        Objects.requireNonNull(operation, "operation");
        ReentrantLock lock = locks[stripe(encodedKey)];
        lock.lock();
        try {
            return operation.get();
        } finally {
            lock.unlock();
        }
    }

    private int stripe(String key) {
        try {
            byte first = MessageDigest.getInstance("SHA-256")
                .digest(key.getBytes(StandardCharsets.UTF_8))[0];
            return Byte.toUnsignedInt(first);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
