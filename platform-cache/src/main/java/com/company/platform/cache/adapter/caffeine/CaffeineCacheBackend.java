package com.company.platform.cache.adapter.caffeine;

import com.company.platform.cache.application.port.out.BackendCacheEntry;
import com.company.platform.cache.application.port.out.BackendClearResult;
import com.company.platform.cache.application.port.out.BackendUpdateResult;
import com.company.platform.cache.application.port.out.CacheBackend;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import com.github.benmanes.caffeine.cache.Ticker;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.UnaryOperator;

/**
 * Bounded in-process backend. Atomicity is limited to this cache instance/JVM.
 */
public final class CaffeineCacheBackend implements CacheBackend {

    private static final int NAMESPACE_TOKEN_BYTES = 16;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final Cache<String, StoredValue> cache;
    private final Ticker ticker;
    private final Duration defaultTtl;
    private final Duration expireAfterAccess;
    private final AtomicReference<String> namespaceToken;
    private final ReentrantReadWriteLock lifecycleLock = new ReentrantReadWriteLock();

    public CaffeineCacheBackend(CaffeineCacheSettings settings) {
        this(settings, Ticker.systemTicker());
    }

    CaffeineCacheBackend(CaffeineCacheSettings settings, Ticker ticker) {
        Objects.requireNonNull(settings, "settings");
        this.ticker = Objects.requireNonNull(ticker, "ticker");
        validateSettings(settings);
        this.defaultTtl = settings.getDefaultTtl();
        this.expireAfterAccess = settings.getExpireAfterAccess();
        this.namespaceToken = new AtomicReference<>(newNamespaceToken());
        this.cache = buildCache(settings);
    }

    @Override
    public Optional<BackendCacheEntry> get(String key) {
        requireKey(key);
        lifecycleLock.readLock().lock();
        try {
            StoredValue value = cache.getIfPresent(key);
            if (value == null) {
                return Optional.empty();
            }
            return Optional.of(toEntry(value, ticker.read()));
        } finally {
            lifecycleLock.readLock().unlock();
        }
    }

    @Override
    public void put(String key, Object value, Duration ttl) {
        requireKey(key);
        Objects.requireNonNull(value, "value");
        lifecycleLock.readLock().lock();
        try {
            long now = ticker.read();
            cache.asMap().compute(key, (ignored, existing) -> newValue(
                value, existing == null ? 1L : existing.version() + 1L, ttl, now));
        } finally {
            lifecycleLock.readLock().unlock();
        }
    }

    @Override
    public void putEntry(String key, BackendCacheEntry entry, Duration ttl) {
        requireKey(key);
        Objects.requireNonNull(entry, "entry");
        lifecycleLock.readLock().lock();
        try {
            cache.put(key, newValue(
                entry.getValue(), entry.getVersion(), ttl, ticker.read()));
        } finally {
            lifecycleLock.readLock().unlock();
        }
    }

    @Override
    public boolean putIfAbsent(String key, Object value, Duration ttl) {
        requireKey(key);
        Objects.requireNonNull(value, "value");
        lifecycleLock.readLock().lock();
        try {
            long now = ticker.read();
            return cache.asMap().putIfAbsent(
                key, newValue(value, 1L, ttl, now)) == null;
        } finally {
            lifecycleLock.readLock().unlock();
        }
    }

    @Override
    public boolean evict(String key) {
        requireKey(key);
        lifecycleLock.readLock().lock();
        try {
            return cache.asMap().remove(key) != null;
        } finally {
            lifecycleLock.readLock().unlock();
        }
    }

    @Override
    public BackendClearResult clear() {
        lifecycleLock.writeLock().lock();
        try {
            cache.cleanUp();
            long count = cache.asMap().size();
            cache.invalidateAll();
            cache.cleanUp();
            String previous = namespaceToken.get();
            String current = newNamespaceToken();
            namespaceToken.set(current);
            return new BackendClearResult(
                "LOCAL_INVALIDATE_ALL", previous, current, count);
        } finally {
            lifecycleLock.writeLock().unlock();
        }
    }

    @Override
    public String namespaceToken() {
        return namespaceToken.get();
    }

    @Override
    public long estimatedSize() {
        lifecycleLock.readLock().lock();
        try {
            cache.cleanUp();
            return cache.estimatedSize();
        } finally {
            lifecycleLock.readLock().unlock();
        }
    }

    @Override
    public long increment(String key, long delta, Duration ttl) {
        requireKey(key);
        lifecycleLock.readLock().lock();
        try {
            long now = ticker.read();
            StoredValue result = cache.asMap().compute(key, (ignored, existing) -> {
                if (existing == null) {
                    return newValue(delta, 1L, ttl, now);
                }
                if (!(existing.value() instanceof Number number)) {
                    throw new IllegalStateException(
                        "Cache counter contains a non-numeric value");
                }
                long incremented = Math.addExact(number.longValue(), delta);
                return existing.withValue(incremented, existing.version() + 1L);
            });
            return ((Number) result.value()).longValue();
        } finally {
            lifecycleLock.readLock().unlock();
        }
    }

    @Override
    public boolean compareAndSet(
        String key, Object expectedValue, Object newValue) {
        requireKey(key);
        Objects.requireNonNull(newValue, "newValue");
        lifecycleLock.readLock().lock();
        try {
            AtomicBoolean updated = new AtomicBoolean();
            cache.asMap().computeIfPresent(key, (ignored, existing) -> {
                if (!Objects.deepEquals(existing.value(), expectedValue)) {
                    return existing;
                }
                updated.set(true);
                return existing.withValue(newValue, existing.version() + 1L);
            });
            return updated.get();
        } finally {
            lifecycleLock.readLock().unlock();
        }
    }

    @Override
    public boolean compareAndDelete(String key, Object expectedValue) {
        requireKey(key);
        lifecycleLock.readLock().lock();
        try {
            AtomicBoolean deleted = new AtomicBoolean();
            cache.asMap().computeIfPresent(key, (ignored, existing) -> {
                if (!Objects.deepEquals(existing.value(), expectedValue)) {
                    return existing;
                }
                deleted.set(true);
                return null;
            });
            return deleted.get();
        } finally {
            lifecycleLock.readLock().unlock();
        }
    }

    @Override
    public BackendUpdateResult updateIfVersion(
        String key, long expectedVersion, Object newValue) {
        requireKey(key);
        Objects.requireNonNull(newValue, "newValue");
        lifecycleLock.readLock().lock();
        try {
            AtomicReference<BackendUpdateResult.Status> status =
                new AtomicReference<>(BackendUpdateResult.Status.NOT_FOUND);
            AtomicReference<StoredValue> result = new AtomicReference<>();
            cache.asMap().computeIfPresent(key, (ignored, existing) -> {
                if (existing.version() != expectedVersion) {
                    status.set(BackendUpdateResult.Status.VERSION_CONFLICT);
                    result.set(existing);
                    return existing;
                }
                StoredValue updated =
                    existing.withValue(newValue, existing.version() + 1L);
                status.set(BackendUpdateResult.Status.UPDATED);
                result.set(updated);
                return updated;
            });
            return new BackendUpdateResult(
                status.get(), toNullableEntry(result.get(), ticker.read()));
        } finally {
            lifecycleLock.readLock().unlock();
        }
    }

    @Override
    public BackendUpdateResult compute(
        String key, UnaryOperator<Object> updater) {
        requireKey(key);
        Objects.requireNonNull(updater, "updater");
        lifecycleLock.readLock().lock();
        try {
            AtomicReference<StoredValue> result = new AtomicReference<>();
            cache.asMap().computeIfPresent(key, (ignored, existing) -> {
                Object updatedValue = Objects.requireNonNull(
                    updater.apply(existing.value()), "updater result");
                StoredValue updated =
                    existing.withValue(updatedValue, existing.version() + 1L);
                result.set(updated);
                return updated;
            });
            StoredValue updated = result.get();
            if (updated == null) {
                return new BackendUpdateResult(
                    BackendUpdateResult.Status.NOT_FOUND, null);
            }
            return new BackendUpdateResult(
                BackendUpdateResult.Status.UPDATED,
                toEntry(updated, ticker.read()));
        } finally {
            lifecycleLock.readLock().unlock();
        }
    }

    private Cache<String, StoredValue> buildCache(CaffeineCacheSettings settings) {
        Caffeine<String, StoredValue> builder =
            Caffeine.<String, StoredValue>newBuilder()
            .ticker(ticker)
            .maximumSize(settings.getMaximumSize())
            .expireAfter(new StoredValueExpiry(expireAfterAccess));
        if (settings.isRecordStats()) {
            builder.recordStats();
        }
        if (settings.isWeakValues()) {
            builder.weakValues();
        } else if (settings.isSoftValues()) {
            builder.softValues();
        }
        return builder.build();
    }

    private StoredValue newValue(
        Object value, long version, Duration ttl, long now) {
        Duration effectiveTtl = ttl == null ? defaultTtl : ttl;
        requirePositive(effectiveTtl, "ttl");
        long ttlNanos = toNanosSaturated(effectiveTtl);
        return new StoredValue(value, version, addSaturated(now, ttlNanos));
    }

    private BackendCacheEntry toNullableEntry(StoredValue value, long now) {
        return value == null ? null : toEntry(value, now);
    }

    private BackendCacheEntry toEntry(StoredValue value, long now) {
        long writeRemaining = Math.max(0L, value.expiresAtNanos() - now);
        long reportedRemaining = expireAfterAccess == null
            ? writeRemaining
            : Math.min(writeRemaining, toNanosSaturated(expireAfterAccess));
        return new BackendCacheEntry(
            value.value(),
            value.version(),
            Duration.ofNanos(reportedRemaining));
    }

    private static void validateSettings(CaffeineCacheSettings settings) {
        if (settings.getMaximumSize() <= 0L) {
            throw new IllegalArgumentException("maximumSize must be positive");
        }
        requirePositive(settings.getDefaultTtl(), "defaultTtl");
        if (settings.getExpireAfterAccess() != null) {
            requirePositive(settings.getExpireAfterAccess(), "expireAfterAccess");
        }
        if (settings.isWeakKeys()) {
            throw new IllegalArgumentException(
                "weakKeys is incompatible with canonical String key equality");
        }
        if (settings.isWeakValues() && settings.isSoftValues()) {
            throw new IllegalArgumentException(
                "weakValues and softValues are mutually exclusive");
        }
    }

    private static String requireKey(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("key must not be blank");
        }
        return key;
    }

    private static void requirePositive(Duration duration, String name) {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }

    private static long toNanosSaturated(Duration duration) {
        try {
            return duration.toNanos();
        } catch (ArithmeticException ignored) {
            return Long.MAX_VALUE;
        }
    }

    private static long addSaturated(long left, long right) {
        if (right > 0L && left > Long.MAX_VALUE - right) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }

    private static String newNamespaceToken() {
        byte[] bytes = new byte[NAMESPACE_TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static final class StoredValue {
        private final Object value;
        private final long version;
        private final long expiresAtNanos;

        private StoredValue(Object value, long version, long expiresAtNanos) {
            this.value = value;
            this.version = version;
            this.expiresAtNanos = expiresAtNanos;
        }

        private Object value() {
            return value;
        }

        private long version() {
            return version;
        }

        private long expiresAtNanos() {
            return expiresAtNanos;
        }

        private StoredValue withValue(Object newValue, long newVersion) {
            return new StoredValue(newValue, newVersion, expiresAtNanos);
        }
    }

    private static final class StoredValueExpiry
        implements Expiry<String, StoredValue> {

        private final long accessTtlNanos;

        private StoredValueExpiry(Duration expireAfterAccess) {
            accessTtlNanos = expireAfterAccess == null
                ? Long.MAX_VALUE
                : toNanosSaturated(expireAfterAccess);
        }

        @Override
        public long expireAfterCreate(
            String key, StoredValue value, long currentTime) {
            return remaining(value, currentTime);
        }

        @Override
        public long expireAfterUpdate(
            String key, StoredValue value, long currentTime,
            long currentDuration) {
            return remaining(value, currentTime);
        }

        @Override
        public long expireAfterRead(
            String key, StoredValue value, long currentTime,
            long currentDuration) {
            return Math.min(remaining(value, currentTime), accessTtlNanos);
        }

        private long remaining(StoredValue value, long currentTime) {
            return Math.max(0L, value.expiresAtNanos() - currentTime);
        }
    }
}
