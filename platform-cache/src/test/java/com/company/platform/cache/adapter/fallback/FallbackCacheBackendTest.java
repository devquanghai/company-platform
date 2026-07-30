package com.company.platform.cache.adapter.fallback;

import com.company.platform.cache.adapter.caffeine.CaffeineCacheBackend;
import com.company.platform.cache.adapter.caffeine.CaffeineCacheSettings;
import com.company.platform.cache.application.port.out.BackendCacheEntry;
import com.company.platform.cache.application.port.out.BackendClearResult;
import com.company.platform.cache.application.port.out.BackendUpdateResult;
import com.company.platform.cache.application.port.out.CacheBackend;
import com.company.platform.cache.domain.model.CacheTier;
import com.company.platform.cache.domain.policy.CacheFallbackMode;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.function.UnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FallbackCacheBackendTest {

    @Test
    void shadowsPrimaryAndServesFreshFallback() {
        SwitchableBackend primary = new SwitchableBackend(local());
        CaffeineCacheBackend fallback = local();
        Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
        FallbackCacheBackend backend = new FallbackCacheBackend(
            primary, fallback, CacheFallbackMode.READ_THROUGH,
            Duration.ofMinutes(2), Duration.ofMinutes(5), true, clock);
        primary.delegate.put("key", "value", Duration.ofMinutes(10));

        assertThat(backend.get("key")).get().extracting(BackendCacheEntry::getValue)
            .isEqualTo("value");
        primary.fail = true;
        BackendCacheEntry degraded = backend.get("key").orElseThrow();
        assertThat(degraded.getValue()).isEqualTo("value");
        assertThat(degraded.getTier()).isEqualTo(CacheTier.FALLBACK);
        assertThat(degraded.isStale()).isFalse();
    }

    @Test
    void servesStaleOnlyInStaleIfErrorMode() {
        CaffeineCacheBackend local = local();
        SwitchableBackend primary = new SwitchableBackend(local());
        Clock start = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
        FallbackCacheBackend writer = new FallbackCacheBackend(
            primary, local, CacheFallbackMode.STALE_IF_ERROR,
            Duration.ofSeconds(1), Duration.ofMinutes(5), false, start);
        primary.delegate.put("key", "value", Duration.ofMinutes(10));
        writer.get("key");
        primary.fail = true;

        Clock later = Clock.fixed(Instant.parse("2026-01-01T00:00:02Z"), ZoneOffset.UTC);
        FallbackCacheBackend stale = new FallbackCacheBackend(
            primary, local, CacheFallbackMode.STALE_IF_ERROR,
            Duration.ofSeconds(1), Duration.ofMinutes(5), false, later);
        assertThat(stale.get("key").orElseThrow().isStale()).isTrue();

        FallbackCacheBackend readThrough = new FallbackCacheBackend(
            primary, local, CacheFallbackMode.READ_THROUGH,
            Duration.ofSeconds(1), Duration.ofMinutes(5), false, later);
        assertThat(readThrough.get("key")).isEmpty();
    }

    @Test
    void localWriteFallbackMustBeExplicit() {
        SwitchableBackend primary = new SwitchableBackend(local());
        primary.fail = true;
        CaffeineCacheBackend local = local();
        FallbackCacheBackend writable = new FallbackCacheBackend(
            primary, local, CacheFallbackMode.LOCAL_READ_WRITE,
            Duration.ofMinutes(1), Duration.ofMinutes(2), true);
        writable.put("key", "value", Duration.ofMinutes(1));
        assertThat(writable.putIfAbsent("other", "v", Duration.ofMinutes(1))).isTrue();
        assertThatThrownBy(() -> writable.increment("count", 2, Duration.ofMinutes(1)))
            .isInstanceOf(RedisConnectionFailureException.class);

        FallbackCacheBackend failClosed = new FallbackCacheBackend(
            primary, local, CacheFallbackMode.LOCAL_READ_WRITE,
            Duration.ofMinutes(1), Duration.ofMinutes(2), false);
        assertThatThrownBy(() -> failClosed.put("x", "v", Duration.ofMinutes(1)))
            .isInstanceOf(RedisConnectionFailureException.class);
    }

    @Test
    void usesLastKnownNamespaceDuringOutageAndDelegatesCoordination() {
        SwitchableBackend primary = new SwitchableBackend(local());
        FallbackCacheBackend backend = new FallbackCacheBackend(
            primary, local(), CacheFallbackMode.NONE,
            Duration.ofMinutes(1), Duration.ofMinutes(2), false);
        String token = backend.namespaceToken();
        primary.fail = true;
        assertThat(backend.namespaceToken()).isEqualTo(token);
        assertThat(backend.get("missing")).isEmpty();
        assertThat(backend.estimatedSize()).isZero();
        assertThatThrownBy(() -> backend.compareAndDelete("x", "v"))
            .isInstanceOf(RedisConnectionFailureException.class);
    }

    @Test
    void successfulPrimaryMutationsShadowOrClearLocalState() {
        SwitchableBackend primary = new SwitchableBackend(local());
        CaffeineCacheBackend fallback = local();
        FallbackCacheBackend backend = new FallbackCacheBackend(
            primary, fallback, CacheFallbackMode.READ_THROUGH,
            Duration.ofMinutes(1), Duration.ofMinutes(2), false);

        backend.put("key", "v1", Duration.ofMinutes(1));
        assertThat(backend.putIfAbsent("key", "ignored", Duration.ofMinutes(1))).isFalse();
        assertThat(backend.putIfAbsent("new", "value", Duration.ofMinutes(1))).isTrue();
        assertThat(backend.increment("count", 2, Duration.ofMinutes(1))).isEqualTo(2);
        assertThat(backend.compareAndSet("key", "v1", "v2")).isTrue();
        assertThat(backend.compareAndSet("key", "wrong", "v3")).isFalse();
        assertThat(backend.updateIfVersion("key", 2, "v3").getEntry().getValue())
            .isEqualTo("v3");
        assertThat(backend.compute("key", value -> value + "!").getEntry().getValue())
            .isEqualTo("v3!");
        assertThat(backend.compareAndDelete("key", "v3!")).isTrue();
        assertThat(backend.evict("new")).isTrue();
        assertThat(backend.clear().getCurrentNamespaceToken()).isNotBlank();

        assertThat(backend.get("unknown")).isEmpty();
        primary.fail = true;
        assertThat(backend.get("new")).isEmpty();
    }

    @Test
    void doesNotFallbackForProgrammingFailuresOrNoneMode() {
        CacheBackend programmingFailure = new SwitchableBackend(local()) {
            @Override public Optional<BackendCacheEntry> get(String key) {
                throw new IllegalArgumentException("bad key");
            }
        };
        FallbackCacheBackend strict = new FallbackCacheBackend(
            programmingFailure, local(), CacheFallbackMode.READ_THROUGH,
            Duration.ofMinutes(1), Duration.ofMinutes(2), false);
        assertThatThrownBy(() -> strict.get("key"))
            .isInstanceOf(IllegalArgumentException.class);

        SwitchableBackend primary = new SwitchableBackend(local());
        primary.fail = true;
        FallbackCacheBackend none = new FallbackCacheBackend(
            primary, local(), CacheFallbackMode.NONE,
            Duration.ofMinutes(1), Duration.ofMinutes(2), false);
        assertThat(none.get("key")).isEmpty();
        assertThat(none.namespaceToken()).isNotBlank();
    }

    @Test
    void clearsFallbackOnRecoveryOnlyWhenConfigured() {
        SwitchableBackend primary = new SwitchableBackend(local());
        CaffeineCacheBackend local = local();
        FallbackCacheBackend clearing = new FallbackCacheBackend(
            primary, local, CacheFallbackMode.READ_THROUGH,
            Duration.ofMinutes(1), Duration.ofMinutes(2), false, true);
        clearing.put("key", "value", Duration.ofMinutes(1));
        String clearingToken = local.namespaceToken();
        primary.fail = true;
        assertThat(clearing.get("key")).isPresent();
        primary.fail = false;
        assertThat(clearing.get("key")).isPresent();
        assertThat(local.namespaceToken()).isNotEqualTo(clearingToken);

        SwitchableBackend retainedPrimary = new SwitchableBackend(local());
        CaffeineCacheBackend retainedLocal = local();
        FallbackCacheBackend retaining = new FallbackCacheBackend(
            retainedPrimary, retainedLocal, CacheFallbackMode.READ_THROUGH,
            Duration.ofMinutes(1), Duration.ofMinutes(2), false, false);
        retaining.put("key", "value", Duration.ofMinutes(1));
        String retainedToken = retainedLocal.namespaceToken();
        retainedPrimary.fail = true;
        assertThat(retaining.get("key")).isPresent();
        retainedPrimary.fail = false;
        assertThat(retaining.get("key")).isPresent();
        assertThat(retainedLocal.namespaceToken()).isEqualTo(retainedToken);
    }

    @Test
    void validatesConstructor() {
        CaffeineCacheBackend local = local();
        assertThatNullPointerException().isThrownBy(() ->
            new FallbackCacheBackend(null, local, CacheFallbackMode.NONE,
                Duration.ofSeconds(1), Duration.ofSeconds(1), false));
        assertThatNullPointerException().isThrownBy(() ->
            new FallbackCacheBackend(local, null, CacheFallbackMode.NONE,
                Duration.ofSeconds(1), Duration.ofSeconds(1), false));
        assertThatIllegalArgumentException().isThrownBy(() ->
            new FallbackCacheBackend(local, local, CacheFallbackMode.NONE,
                Duration.ZERO, Duration.ofSeconds(1), false));
    }

    private CaffeineCacheBackend local() {
        return new CaffeineCacheBackend(CaffeineCacheSettings.builder()
            .maximumSize(100)
            .defaultTtl(Duration.ofMinutes(10))
            .build());
    }

    private static class SwitchableBackend implements CacheBackend {
        private final CacheBackend delegate;
        private boolean fail;
        private SwitchableBackend(CacheBackend delegate) { this.delegate = delegate; }
        @Override public Optional<BackendCacheEntry> get(String key) { check(); return delegate.get(key); }
        @Override public void put(String key, Object value, Duration ttl) { check(); delegate.put(key, value, ttl); }
        @Override public boolean putIfAbsent(String key, Object value, Duration ttl) { check(); return delegate.putIfAbsent(key, value, ttl); }
        @Override public boolean evict(String key) { check(); return delegate.evict(key); }
        @Override public BackendClearResult clear() { check(); return delegate.clear(); }
        @Override public String namespaceToken() { check(); return delegate.namespaceToken(); }
        @Override public long estimatedSize() { check(); return delegate.estimatedSize(); }
        @Override public long increment(String key, long delta, Duration ttl) { check(); return delegate.increment(key, delta, ttl); }
        @Override public boolean compareAndSet(String key, Object expectedValue, Object newValue) { check(); return delegate.compareAndSet(key, expectedValue, newValue); }
        @Override public boolean compareAndDelete(String key, Object expectedValue) { check(); return delegate.compareAndDelete(key, expectedValue); }
        @Override public BackendUpdateResult updateIfVersion(String key, long expectedVersion, Object newValue) { check(); return delegate.updateIfVersion(key, expectedVersion, newValue); }
        @Override public BackendUpdateResult compute(String key, UnaryOperator<Object> updater) { check(); return delegate.compute(key, updater); }
        private void check() {
            if (fail) {
                throw new RedisConnectionFailureException("redis unavailable");
            }
        }
    }
}
