package com.company.platform.cache.adapter.multilevel;

import com.company.platform.cache.adapter.caffeine.CaffeineCacheBackend;
import com.company.platform.cache.adapter.caffeine.CaffeineCacheSettings;
import com.company.platform.cache.application.port.out.BackendCacheEntry;
import com.company.platform.cache.application.port.out.BackendClearResult;
import com.company.platform.cache.application.port.out.BackendUpdateResult;
import com.company.platform.cache.application.port.out.CacheBackend;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;
import java.util.function.UnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MultiLevelCacheBackendTest {

    @Test
    void readsL1ThenPopulatesItFromL2() {
        CaffeineCacheBackend l1 = local();
        CaffeineCacheBackend l2 = local();
        l2.put("key", "remote", Duration.ofMinutes(2));
        MultiLevelCacheBackend backend =
            new MultiLevelCacheBackend(l1, l2, Duration.ofSeconds(30), true);

        assertThat(backend.get("key")).get().extracting(BackendCacheEntry::getValue)
            .isEqualTo("remote");
        l2.evict("key");
        assertThat(backend.get("key")).get().extracting(BackendCacheEntry::getValue)
            .isEqualTo("remote");
        assertThat(backend.estimatedSize()).isEqualTo(1);
        assertThat(backend.namespaceToken()).isEqualTo(l2.namespaceToken());
    }

    @Test
    void supportsMutationsClearAndAtomicOperations() {
        CaffeineCacheBackend l1 = local();
        CaffeineCacheBackend l2 = local();
        MultiLevelCacheBackend backend =
            new MultiLevelCacheBackend(l1, l2, Duration.ofSeconds(30), true);

        backend.put("key", "v1", Duration.ofMinutes(1));
        assertThat(l1.get("key")).isPresent();
        assertThat(l2.get("key")).isPresent();
        assertThat(backend.putIfAbsent("key", "ignored", Duration.ofMinutes(1))).isFalse();
        assertThat(backend.putIfAbsent("new", "value", Duration.ofMinutes(1))).isTrue();
        assertThat(backend.increment("count", 2, Duration.ofMinutes(1))).isEqualTo(2);
        assertThat(backend.compareAndSet("key", "v1", "v2")).isTrue();
        assertThat(backend.compareAndSet("key", "wrong", "v3")).isFalse();
        assertThat(backend.updateIfVersion("key", 2, "v3").getStatus())
            .isEqualTo(BackendUpdateResult.Status.UPDATED);
        assertThat(backend.compute("key", value -> value + "!").getEntry().getValue())
            .isEqualTo("v3!");
        assertThat(backend.compareAndDelete("key", "v3!")).isTrue();
        assertThat(backend.evict("new")).isTrue();

        BackendClearResult clear = backend.clear();
        assertThat(clear.getCurrentNamespaceToken()).isEqualTo(l2.namespaceToken());
        assertThat(l1.estimatedSize()).isZero();
    }

    @Test
    void leavesKeyFailClosedAfterL2MutationFailure() {
        CaffeineCacheBackend l1 = local();
        l1.put("key", "old", Duration.ofMinutes(1));
        CacheBackend failing = new FailingBackend();
        MultiLevelCacheBackend backend =
            new MultiLevelCacheBackend(l1, failing, Duration.ofSeconds(30), true);

        assertThatThrownBy(() -> backend.put("key", "new", Duration.ofMinutes(1)))
            .isInstanceOf(IllegalStateException.class);
        assertThat(backend.get("key")).isEmpty();
    }

    @Test
    void canDisableL1PopulationAndValidatesConstructor() {
        CaffeineCacheBackend l1 = local();
        CaffeineCacheBackend l2 = local();
        l2.put("key", "remote", Duration.ofMinutes(1));
        MultiLevelCacheBackend backend =
            new MultiLevelCacheBackend(l1, l2, Duration.ofSeconds(30), false);
        assertThat(backend.get("key")).isPresent();
        l2.evict("key");
        assertThat(backend.get("key")).isEmpty();

        assertThatNullPointerException().isThrownBy(() ->
            new MultiLevelCacheBackend(null, l2, Duration.ofSeconds(1), true));
        assertThatNullPointerException().isThrownBy(() ->
            new MultiLevelCacheBackend(l1, null, Duration.ofSeconds(1), true));
        assertThatIllegalArgumentException().isThrownBy(() ->
            new MultiLevelCacheBackend(l1, l2, Duration.ZERO, true));
    }

    private CaffeineCacheBackend local() {
        return new CaffeineCacheBackend(CaffeineCacheSettings.builder()
            .maximumSize(100)
            .defaultTtl(Duration.ofMinutes(10))
            .build());
    }

    private static final class FailingBackend implements CacheBackend {
        @Override public Optional<BackendCacheEntry> get(String key) { return Optional.empty(); }
        @Override public void put(String key, Object value, Duration ttl) { throw failure(); }
        @Override public boolean putIfAbsent(String key, Object value, Duration ttl) { throw failure(); }
        @Override public boolean evict(String key) { throw failure(); }
        @Override public BackendClearResult clear() { throw failure(); }
        @Override public String namespaceToken() { return "abcdefghijklmnop"; }
        @Override public long estimatedSize() { return 0; }
        @Override public long increment(String key, long delta, Duration ttl) { throw failure(); }
        @Override public boolean compareAndSet(String key, Object expectedValue, Object newValue) { throw failure(); }
        @Override public boolean compareAndDelete(String key, Object expectedValue) { throw failure(); }
        @Override public BackendUpdateResult updateIfVersion(String key, long expectedVersion, Object newValue) { throw failure(); }
        @Override public BackendUpdateResult compute(String key, UnaryOperator<Object> updater) { throw failure(); }
        private IllegalStateException failure() { return new IllegalStateException("redis unavailable"); }
    }
}
