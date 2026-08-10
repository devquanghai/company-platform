package com.company.platform.cache.internal.adapter.fallback;

import com.company.platform.cache.internal.application.port.out.BackendCacheEntry;
import com.company.platform.cache.internal.application.port.out.BackendClearResult;
import com.company.platform.cache.internal.application.port.out.BackendUpdateResult;
import com.company.platform.cache.internal.application.port.out.CacheBackend;
import com.company.platform.cache.domain.model.CacheTier;
import com.company.platform.cache.domain.policy.CacheFallbackMode;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.UnaryOperator;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;

public final class FallbackCacheBackend implements CacheBackend {
    private final CacheBackend primary;
    private final CacheBackend local;
    private final CacheFallbackMode mode;
    private final Duration localTtl;
    private final Duration maximumStale;
    private final boolean localWriteFallback;
    private final boolean clearOnPrimaryRecovery;
    private final Clock clock;
    private final AtomicReference<String> lastNamespace = new AtomicReference<>();
    private final AtomicBoolean degraded = new AtomicBoolean();

    public FallbackCacheBackend(
        CacheBackend primary,
        CacheBackend local,
        CacheFallbackMode mode,
        Duration localTtl,
        Duration maximumStale,
        boolean localWriteFallback
    ) {
        this(primary, local, mode, localTtl, maximumStale,
            localWriteFallback, true, Clock.systemUTC());
    }

    public FallbackCacheBackend(
        CacheBackend primary,
        CacheBackend local,
        CacheFallbackMode mode,
        Duration localTtl,
        Duration maximumStale,
        boolean localWriteFallback,
        boolean clearOnPrimaryRecovery
    ) {
        this(primary, local, mode, localTtl, maximumStale,
            localWriteFallback, clearOnPrimaryRecovery, Clock.systemUTC());
    }

    FallbackCacheBackend(
        CacheBackend primary,
        CacheBackend local,
        CacheFallbackMode mode,
        Duration localTtl,
        Duration maximumStale,
        boolean localWriteFallback,
        Clock clock
    ) {
        this(primary, local, mode, localTtl, maximumStale,
            localWriteFallback, true, clock);
    }

    FallbackCacheBackend(
        CacheBackend primary,
        CacheBackend local,
        CacheFallbackMode mode,
        Duration localTtl,
        Duration maximumStale,
        boolean localWriteFallback,
        boolean clearOnPrimaryRecovery,
        Clock clock
    ) {
        this.primary = Objects.requireNonNull(primary, "primary");
        this.local = Objects.requireNonNull(local, "local");
        this.mode = Objects.requireNonNull(mode, "mode");
        this.localTtl = positive(localTtl, "localTtl");
        this.maximumStale = positive(maximumStale, "maximumStale");
        this.localWriteFallback = localWriteFallback;
        this.clearOnPrimaryRecovery = clearOnPrimaryRecovery;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public Optional<BackendCacheEntry> get(String key) {
        try {
            Optional<BackendCacheEntry> primaryValue = primary.get(key);
            recoverIfNecessary();
            if (primaryValue.isPresent()) {
                BackendCacheEntry entry = primaryValue.get();
                shadow(key, entry.getValue(), entry.getVersion(), entry.getRemainingTtl());
            } else {
                local.evict(key);
            }
            return primaryValue;
        } catch (RuntimeException primaryFailure) {
            requireInfrastructure(primaryFailure);
            degraded.set(true);
            return fallbackGet(key);
        }
    }

    @Override
    public void put(String key, Object value, Duration ttl) {
        try {
            primary.put(key, value, ttl);
            recoverIfNecessary();
            shadow(key, value, 1L, ttl);
        } catch (RuntimeException failure) {
            requireInfrastructure(failure);
            degraded.set(true);
            if (allowsLocalWrite()) {
                shadow(key, value, 1L, localTtl);
                return;
            }
            throw failure;
        }
    }

    @Override
    public boolean putIfAbsent(String key, Object value, Duration ttl) {
        try {
            boolean stored = primary.putIfAbsent(key, value, ttl);
            recoverIfNecessary();
            if (stored) {
                shadow(key, value, 1L, ttl);
            }
            return stored;
        } catch (RuntimeException failure) {
            requireInfrastructure(failure);
            degraded.set(true);
            if (allowsLocalWrite()) {
                return local.putIfAbsent(
                    key, new FallbackValue(
                        value, clock.instant().plus(localTtl)), physicalTtl());
            }
            throw failure;
        }
    }

    @Override
    public boolean evict(String key) {
        local.evict(key);
        return primary.evict(key);
    }

    @Override
    public BackendClearResult clear() {
        local.clear();
        return primary.clear();
    }

    @Override
    public String namespaceToken() {
        try {
            String token = primary.namespaceToken();
            lastNamespace.set(token);
            return token;
        } catch (RuntimeException failure) {
            requireInfrastructure(failure);
            String token = lastNamespace.get();
            return token == null ? local.namespaceToken() : token;
        }
    }

    @Override
    public long estimatedSize() {
        return local.estimatedSize();
    }

    @Override
    public long increment(String key, long delta, Duration ttl) {
        try {
            long result = primary.increment(key, delta, ttl);
            recoverIfNecessary();
            shadow(key, result, 1L, ttl);
            return result;
        } catch (RuntimeException failure) {
            requireInfrastructure(failure);
            throw failure;
        }
    }

    @Override
    public boolean compareAndSet(
        String key, Object expectedValue, Object newValue
    ) {
        try {
            boolean result = primary.compareAndSet(key, expectedValue, newValue);
            recoverIfNecessary();
            if (result) {
                shadow(key, newValue, 1L, localTtl);
            }
            return result;
        } catch (RuntimeException failure) {
            requireInfrastructure(failure);
            throw failure;
        }
    }

    @Override
    public boolean compareAndDelete(String key, Object expectedValue) {
        local.evict(key);
        return primary.compareAndDelete(key, expectedValue);
    }

    @Override
    public BackendUpdateResult updateIfVersion(
        String key, long expectedVersion, Object newValue
    ) {
        return primary.updateIfVersion(key, expectedVersion, newValue);
    }

    @Override
    public BackendUpdateResult compute(
        String key, UnaryOperator<Object> updater
    ) {
        return primary.compute(key, updater);
    }

    private Optional<BackendCacheEntry> fallbackGet(String key) {
        if (mode == CacheFallbackMode.NONE) {
            return Optional.empty();
        }
        Optional<BackendCacheEntry> found = local.get(key);
        if (found.isEmpty() || !(found.get().getValue() instanceof FallbackValue fallback)) {
            return Optional.empty();
        }
        boolean stale = clock.instant().isAfter(fallback.getFreshUntil());
        if (stale && mode != CacheFallbackMode.STALE_IF_ERROR) {
            return Optional.empty();
        }
        BackendCacheEntry entry = found.get();
        return Optional.of(new BackendCacheEntry(
            fallback.getValue(), entry.getVersion(), entry.getRemainingTtl(),
            stale, CacheTier.FALLBACK));
    }

    private void shadow(
        String key, Object value, long ignoredVersion, Duration primaryRemainingTtl
    ) {
        Duration freshTtl = primaryRemainingTtl == null
            || primaryRemainingTtl.isZero() || primaryRemainingTtl.isNegative()
            ? localTtl
            : primaryRemainingTtl.compareTo(localTtl) < 0
                ? primaryRemainingTtl : localTtl;
        Duration physicalTtl = freshTtl.plus(maximumStale);
        local.putEntry(key, new BackendCacheEntry(
            new FallbackValue(value, clock.instant().plus(freshTtl)),
            ignoredVersion, physicalTtl), physicalTtl);
    }

    private Duration physicalTtl() {
        return localTtl.plus(maximumStale);
    }

    private boolean allowsLocalWrite() {
        return mode == CacheFallbackMode.READ_THROUGH
            || (mode == CacheFallbackMode.LOCAL_READ_WRITE && localWriteFallback);
    }

    private Duration positive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    private void requireInfrastructure(RuntimeException failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof RedisConnectionFailureException
                || current instanceof QueryTimeoutException
                || current instanceof CallNotPermittedException
                || current instanceof BulkheadFullException
                || current instanceof java.net.ConnectException
                || current instanceof java.net.SocketTimeoutException) {
                return;
            }
            current = current.getCause();
        }
        throw failure;
    }

    private void recoverIfNecessary() {
        if (degraded.compareAndSet(true, false) && clearOnPrimaryRecovery) {
            local.clear();
        }
    }

    public static final class FallbackValue {
        private final Object value;
        private final Instant freshUntil;

        public FallbackValue(Object value, Instant freshUntil) {
            this.value = value;
            this.freshUntil = Objects.requireNonNull(freshUntil, "freshUntil");
        }

        public Object getValue() {
            return value;
        }

        public Instant getFreshUntil() {
            return freshUntil;
        }
    }
}
