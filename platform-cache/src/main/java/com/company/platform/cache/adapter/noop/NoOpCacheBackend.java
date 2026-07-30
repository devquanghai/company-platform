package com.company.platform.cache.adapter.noop;

import com.company.platform.cache.application.port.out.BackendCacheEntry;
import com.company.platform.cache.application.port.out.BackendClearResult;
import com.company.platform.cache.application.port.out.BackendUpdateResult;
import com.company.platform.cache.application.port.out.CacheBackend;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;

/**
 * Disabled-cache backend: reads always miss and mutations never retain data.
 */
public final class NoOpCacheBackend implements CacheBackend {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private final AtomicReference<String> namespaceToken =
        new AtomicReference<>(newNamespaceToken());

    @Override
    public Optional<BackendCacheEntry> get(String key) {
        requireKey(key);
        return Optional.empty();
    }

    @Override
    public void put(String key, Object value, Duration ttl) {
        requireMutation(key, value, ttl);
    }

    @Override
    public boolean putIfAbsent(String key, Object value, Duration ttl) {
        requireMutation(key, value, ttl);
        return false;
    }

    @Override
    public boolean evict(String key) {
        requireKey(key);
        return false;
    }

    @Override
    public BackendClearResult clear() {
        String previous = namespaceToken.get();
        String current = newNamespaceToken();
        namespaceToken.set(current);
        return new BackendClearResult("NOOP", previous, current, 0L);
    }

    @Override
    public String namespaceToken() {
        return namespaceToken.get();
    }

    @Override
    public long estimatedSize() {
        return 0L;
    }

    @Override
    public long increment(String key, long delta, Duration ttl) {
        requireKey(key);
        requireTtl(ttl);
        return 0L;
    }

    @Override
    public boolean compareAndSet(
        String key, Object expectedValue, Object newValue) {
        requireKey(key);
        if (newValue == null) {
            throw new NullPointerException("newValue");
        }
        return false;
    }

    @Override
    public boolean compareAndDelete(String key, Object expectedValue) {
        requireKey(key);
        return false;
    }

    @Override
    public BackendUpdateResult updateIfVersion(
        String key, long expectedVersion, Object newValue) {
        requireKey(key);
        if (newValue == null) {
            throw new NullPointerException("newValue");
        }
        return notFound();
    }

    @Override
    public BackendUpdateResult compute(
        String key, UnaryOperator<Object> updater) {
        requireKey(key);
        if (updater == null) {
            throw new NullPointerException("updater");
        }
        return notFound();
    }

    private BackendUpdateResult notFound() {
        return new BackendUpdateResult(
            BackendUpdateResult.Status.NOT_FOUND, null);
    }

    private void requireMutation(String key, Object value, Duration ttl) {
        requireKey(key);
        if (value == null) {
            throw new NullPointerException("value");
        }
        requireTtl(ttl);
    }

    private void requireTtl(Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("ttl must be positive");
        }
    }

    private String requireKey(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("key must not be blank");
        }
        return key;
    }

    private static String newNamespaceToken() {
        byte[] bytes = new byte[16];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
