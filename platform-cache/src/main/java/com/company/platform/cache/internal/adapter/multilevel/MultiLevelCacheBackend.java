package com.company.platform.cache.internal.adapter.multilevel;

import com.company.platform.cache.internal.application.port.out.BackendCacheEntry;
import com.company.platform.cache.internal.application.port.out.BackendClearResult;
import com.company.platform.cache.internal.application.port.out.BackendUpdateResult;
import com.company.platform.cache.internal.application.port.out.CacheBackend;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;

public final class MultiLevelCacheBackend implements CacheBackend {
    private final CacheBackend l1;
    private final CacheBackend l2;
    private final Duration maximumL1Ttl;
    private final boolean populateL1OnL2Hit;
    private final Set<String> dirtyDoNotPopulate = ConcurrentHashMap.newKeySet();

    public MultiLevelCacheBackend(
        CacheBackend l1,
        CacheBackend l2,
        Duration maximumL1Ttl,
        boolean populateL1OnL2Hit
    ) {
        this.l1 = Objects.requireNonNull(l1, "l1");
        this.l2 = Objects.requireNonNull(l2, "l2");
        if (maximumL1Ttl == null || maximumL1Ttl.isZero() || maximumL1Ttl.isNegative()) {
            throw new IllegalArgumentException("maximumL1Ttl must be positive");
        }
        this.maximumL1Ttl = maximumL1Ttl;
        this.populateL1OnL2Hit = populateL1OnL2Hit;
    }

    @Override
    public Optional<BackendCacheEntry> get(String key) {
        if (dirtyDoNotPopulate.contains(key)) {
            return Optional.empty();
        }
        Optional<BackendCacheEntry> local = l1.get(key);
        if (local.isPresent()) {
            return local;
        }
        Optional<BackendCacheEntry> remote = l2.get(key);
        if (remote.isPresent() && populateL1OnL2Hit && !dirtyDoNotPopulate.contains(key)) {
            BackendCacheEntry entry = remote.get();
            l1.putEntry(key, entry, l1Ttl(entry.getRemainingTtl()));
        }
        return remote;
    }

    @Override
    public void put(String key, Object value, Duration ttl) {
        l1.evict(key);
        dirtyDoNotPopulate.add(key);
        l2.put(key, value, ttl);
        repopulate(key);
        dirtyDoNotPopulate.remove(key);
    }

    @Override
    public boolean putIfAbsent(String key, Object value, Duration ttl) {
        l1.evict(key);
        dirtyDoNotPopulate.add(key);
        boolean stored = l2.putIfAbsent(key, value, ttl);
        if (stored) {
            repopulate(key);
        }
        dirtyDoNotPopulate.remove(key);
        return stored;
    }

    @Override
    public boolean evict(String key) {
        l1.evict(key);
        dirtyDoNotPopulate.add(key);
        boolean removed = l2.evict(key);
        dirtyDoNotPopulate.remove(key);
        return removed;
    }

    @Override
    public BackendClearResult clear() {
        l1.clear();
        dirtyDoNotPopulate.clear();
        return l2.clear();
    }

    @Override
    public String namespaceToken() {
        return l2.namespaceToken();
    }

    @Override
    public long estimatedSize() {
        return l1.estimatedSize();
    }

    @Override
    public long increment(String key, long delta, Duration ttl) {
        l1.evict(key);
        dirtyDoNotPopulate.add(key);
        long result = l2.increment(key, delta, ttl);
        repopulate(key);
        dirtyDoNotPopulate.remove(key);
        return result;
    }

    @Override
    public boolean compareAndSet(
        String key, Object expectedValue, Object newValue
    ) {
        l1.evict(key);
        dirtyDoNotPopulate.add(key);
        boolean updated = l2.compareAndSet(key, expectedValue, newValue);
        if (updated) {
            repopulate(key);
        }
        dirtyDoNotPopulate.remove(key);
        return updated;
    }

    @Override
    public boolean compareAndDelete(String key, Object expectedValue) {
        l1.evict(key);
        dirtyDoNotPopulate.add(key);
        boolean deleted = l2.compareAndDelete(key, expectedValue);
        dirtyDoNotPopulate.remove(key);
        return deleted;
    }

    @Override
    public BackendUpdateResult updateIfVersion(
        String key, long expectedVersion, Object newValue
    ) {
        l1.evict(key);
        dirtyDoNotPopulate.add(key);
        BackendUpdateResult result = l2.updateIfVersion(key, expectedVersion, newValue);
        if (result.getStatus() == BackendUpdateResult.Status.UPDATED) {
            cacheEntry(key, result.getEntry());
        }
        dirtyDoNotPopulate.remove(key);
        return result;
    }

    @Override
    public BackendUpdateResult compute(
        String key, UnaryOperator<Object> updater
    ) {
        l1.evict(key);
        dirtyDoNotPopulate.add(key);
        BackendUpdateResult result = l2.compute(key, updater);
        if (result.getStatus() == BackendUpdateResult.Status.UPDATED) {
            cacheEntry(key, result.getEntry());
        }
        dirtyDoNotPopulate.remove(key);
        return result;
    }

    private void repopulate(String key) {
        l2.get(key).ifPresent(entry -> cacheEntry(key, entry));
    }

    private void cacheEntry(String key, BackendCacheEntry entry) {
        if (entry != null) {
            l1.putEntry(key, entry, l1Ttl(entry.getRemainingTtl()));
        }
    }

    private Duration l1Ttl(Duration l2Remaining) {
        if (l2Remaining == null || l2Remaining.isZero() || l2Remaining.isNegative()) {
            return Duration.ofNanos(1);
        }
        return l2Remaining.compareTo(maximumL1Ttl) < 0
            ? l2Remaining : maximumL1Ttl;
    }
}
