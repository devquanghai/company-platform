package com.company.platform.cache.adapter.caffeine;

import com.company.platform.cache.application.port.out.BackendCacheEntry;
import com.company.platform.cache.application.port.out.BackendClearResult;
import com.company.platform.cache.application.port.out.BackendUpdateResult;
import com.github.benmanes.caffeine.cache.Ticker;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CaffeineCacheBackendTest {

    private final MutableTicker ticker = new MutableTicker();

    @Test
    void supportsBoundedCrudTtlAndLogicalClear() {
        CaffeineCacheBackend backend = backend(2, Duration.ofMinutes(5), null);

        backend.put("a", "one", Duration.ofSeconds(10));
        assertThat(backend.putIfAbsent("a", "ignored", Duration.ofSeconds(10)))
            .isFalse();
        assertThat(backend.putIfAbsent("b", "two", null)).isTrue();

        BackendCacheEntry first = backend.get("a").orElseThrow();
        assertThat(first.getValue()).isEqualTo("one");
        assertThat(first.getVersion()).isEqualTo(1L);
        assertThat(first.getRemainingTtl()).isEqualTo(Duration.ofSeconds(10));
        assertThat(backend.estimatedSize()).isEqualTo(2L);

        String oldToken = backend.namespaceToken();
        BackendClearResult clear = backend.clear();
        assertThat(clear.getStrategy()).isEqualTo("LOCAL_INVALIDATE_ALL");
        assertThat(clear.getPreviousNamespaceToken()).isEqualTo(oldToken);
        assertThat(clear.getCurrentNamespaceToken())
            .isNotBlank()
            .isNotEqualTo(oldToken)
            .isEqualTo(backend.namespaceToken());
        assertThat(clear.getExactDeletedCount()).isEqualTo(2L);
        assertThat(backend.get("a")).isEmpty();
        assertThat(backend.estimatedSize()).isZero();
    }

    @Test
    void expiresEntriesAndHonorsAccessExpiry() {
        CaffeineCacheBackend ttlBackend =
            backend(10, Duration.ofSeconds(5), null);
        ttlBackend.put("key", "value", null);
        ticker.advance(Duration.ofSeconds(4));
        assertThat(ttlBackend.get("key")).isPresent();
        ticker.advance(Duration.ofSeconds(2));
        assertThat(ttlBackend.get("key")).isEmpty();

        MutableTicker accessTicker = new MutableTicker();
        CaffeineCacheBackend accessBackend = new CaffeineCacheBackend(
            settings(10, Duration.ofMinutes(1), Duration.ofSeconds(3)),
            accessTicker);
        accessBackend.put("key", "value", null);
        BackendCacheEntry entry = accessBackend.get("key").orElseThrow();
        assertThat(entry.getRemainingTtl()).isEqualTo(Duration.ofSeconds(3));
        accessTicker.advance(Duration.ofSeconds(4));
        assertThat(accessBackend.get("key")).isEmpty();
    }

    @Test
    void enforcesMaximumSize() {
        CaffeineCacheBackend backend = backend(1, Duration.ofMinutes(1), null);
        backend.put("first", "one", null);
        backend.put("second", "two", null);

        assertThat(backend.estimatedSize()).isLessThanOrEqualTo(1L);
    }

    @Test
    void evictsExistingOnly() {
        CaffeineCacheBackend backend = backend(10, Duration.ofMinutes(1), null);
        assertThat(backend.evict("missing")).isFalse();
        backend.put("key", "value", null);
        assertThat(backend.evict("key")).isTrue();
        assertThat(backend.evict("key")).isFalse();
    }

    @Test
    void incrementsAtomicallyAndPreservesExistingExpiry() {
        CaffeineCacheBackend backend = backend(10, Duration.ofMinutes(1), null);
        assertThat(backend.increment("counter", 2L, Duration.ofSeconds(10)))
            .isEqualTo(2L);
        ticker.advance(Duration.ofSeconds(1));
        assertThat(backend.increment("counter", 3L, null)).isEqualTo(5L);
        BackendCacheEntry entry = backend.get("counter").orElseThrow();
        assertThat(entry.getVersion()).isEqualTo(2L);
        assertThat(entry.getRemainingTtl()).isEqualTo(Duration.ofSeconds(9));

        backend.put("text", "not-a-number", null);
        assertThatIllegalStateException()
            .isThrownBy(() -> backend.increment("text", 1L, null))
            .withMessage("Cache counter contains a non-numeric value");

        backend.put("overflow", Long.MAX_VALUE, null);
        assertThatThrownBy(() -> backend.increment("overflow", 1L, null))
            .isInstanceOf(ArithmeticException.class);
    }

    @Test
    void serializesConcurrentIncrementPerKey() throws Exception {
        CaffeineCacheBackend backend = backend(10, Duration.ofMinutes(1), null);
        int workers = 8;
        int incrementsPerWorker = 100;
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(workers)) {
            @SuppressWarnings("unchecked")
            Future<Void>[] futures = new Future[workers];
            for (int worker = 0; worker < workers; worker++) {
                futures[worker] = executor.submit(() -> {
                    start.await();
                    for (int index = 0; index < incrementsPerWorker; index++) {
                        backend.increment("counter", 1L, Duration.ofMinutes(1));
                    }
                    return null;
                });
            }
            start.countDown();
            for (Future<Void> future : futures) {
                future.get();
            }
        }

        assertThat(backend.get("counter").orElseThrow().getValue())
            .isEqualTo((long) workers * incrementsPerWorker);
    }

    @Test
    void supportsCompareAndSetAndCompareAndDelete() {
        CaffeineCacheBackend backend = backend(10, Duration.ofMinutes(1), null);
        assertThat(backend.compareAndSet("missing", "a", "b")).isFalse();
        backend.put("key", new byte[]{1, 2}, null);
        assertThat(backend.compareAndSet(
            "key", new byte[]{1, 2}, new byte[]{3, 4})).isTrue();
        assertThat(backend.get("key").orElseThrow().getVersion()).isEqualTo(2L);
        assertThat(backend.compareAndSet(
            "key", new byte[]{8}, new byte[]{9})).isFalse();
        assertThat(backend.compareAndDelete("key", new byte[]{8})).isFalse();
        assertThat(backend.compareAndDelete(
            "key", new byte[]{3, 4})).isTrue();
        assertThat(backend.compareAndDelete("missing", null)).isFalse();
    }

    @Test
    void supportsOptimisticVersionUpdates() {
        CaffeineCacheBackend backend = backend(10, Duration.ofMinutes(1), null);

        BackendUpdateResult missing =
            backend.updateIfVersion("missing", 1L, "new");
        assertThat(missing.getStatus())
            .isEqualTo(BackendUpdateResult.Status.NOT_FOUND);
        assertThat(missing.getEntry()).isNull();

        backend.put("key", "v1", null);
        BackendUpdateResult conflict =
            backend.updateIfVersion("key", 7L, "v2");
        assertThat(conflict.getStatus())
            .isEqualTo(BackendUpdateResult.Status.VERSION_CONFLICT);
        assertThat(conflict.getEntry().getValue()).isEqualTo("v1");

        BackendUpdateResult updated =
            backend.updateIfVersion("key", 1L, "v2");
        assertThat(updated.getStatus())
            .isEqualTo(BackendUpdateResult.Status.UPDATED);
        assertThat(updated.getEntry().getValue()).isEqualTo("v2");
        assertThat(updated.getEntry().getVersion()).isEqualTo(2L);
    }

    @Test
    void supportsAtomicComputeForExistingEntry() {
        CaffeineCacheBackend backend = backend(10, Duration.ofMinutes(1), null);

        BackendUpdateResult missing =
            backend.compute("missing", value -> "never");
        assertThat(missing.getStatus())
            .isEqualTo(BackendUpdateResult.Status.NOT_FOUND);

        backend.put("key", 2L, null);
        BackendUpdateResult updated =
            backend.compute("key", value -> ((Long) value) * 3L);
        assertThat(updated.getStatus())
            .isEqualTo(BackendUpdateResult.Status.UPDATED);
        assertThat(updated.getEntry().getValue()).isEqualTo(6L);
        assertThat(updated.getEntry().getVersion()).isEqualTo(2L);

        assertThatNullPointerException()
            .isThrownBy(() -> backend.compute("key", value -> null))
            .withMessage("updater result");
        assertThat(backend.get("key").orElseThrow().getValue()).isEqualTo(6L);
    }

    @Test
    void rejectsInvalidSettings() {
        assertThatNullPointerException()
            .isThrownBy(() -> new CaffeineCacheBackend(null));
        assertThatNullPointerException()
            .isThrownBy(() -> new CaffeineCacheBackend(
                settings(1, Duration.ofSeconds(1), null), null));
        assertThatIllegalArgumentException()
            .isThrownBy(() -> backend(0, Duration.ofSeconds(1), null))
            .withMessage("maximumSize must be positive");
        assertThatIllegalArgumentException()
            .isThrownBy(() -> backend(1, Duration.ZERO, null))
            .withMessage("defaultTtl must be positive");
        assertThatIllegalArgumentException()
            .isThrownBy(() -> backend(
                1, Duration.ofSeconds(1), Duration.ofSeconds(-1)))
            .withMessage("expireAfterAccess must be positive");
        CaffeineCacheSettings weakKeys = CaffeineCacheSettings.builder()
            .maximumSize(1)
            .defaultTtl(Duration.ofSeconds(1))
            .weakKeys(true)
            .build();
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new CaffeineCacheBackend(weakKeys))
            .withMessage(
                "weakKeys is incompatible with canonical String key equality");

        CaffeineCacheSettings conflicting = CaffeineCacheSettings.builder()
            .maximumSize(1)
            .defaultTtl(Duration.ofSeconds(1))
            .weakValues(true)
            .softValues(true)
            .build();
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new CaffeineCacheBackend(conflicting))
            .withMessage("weakValues and softValues are mutually exclusive");
    }

    @Test
    void validatesInputsAndBuildsSupportedReferenceModes() {
        CaffeineCacheBackend backend = backend(10, Duration.ofMinutes(1), null);

        assertThatIllegalArgumentException()
            .isThrownBy(() -> backend.get(" "));
        assertThatIllegalArgumentException()
            .isThrownBy(() -> backend.put("key", "value", Duration.ZERO));
        assertThatIllegalArgumentException()
            .isThrownBy(() -> backend.putIfAbsent(
                "key", "value", Duration.ofSeconds(-1)));
        assertThatNullPointerException()
            .isThrownBy(() -> backend.put("key", null, null));
        assertThatNullPointerException()
            .isThrownBy(() -> backend.compareAndSet(
                "key", null, null));
        assertThatNullPointerException()
            .isThrownBy(() -> backend.updateIfVersion(
                "key", 1L, null));
        assertThatNullPointerException()
            .isThrownBy(() -> backend.compute("key", null));

        CaffeineCacheBackend veryLongTtlBackend = new CaffeineCacheBackend(
            CaffeineCacheSettings.builder()
                .maximumSize(1)
                .defaultTtl(Duration.ofSeconds(Long.MAX_VALUE))
                .recordStats(false)
                .weakValues(true)
                .build());
        veryLongTtlBackend.put("key", "value", null);
        assertThat(veryLongTtlBackend.get("key")).isPresent();
        assertThat(new CaffeineCacheBackend(
            CaffeineCacheSettings.builder()
                .maximumSize(1)
                .defaultTtl(Duration.ofMinutes(1))
                .softValues(true)
                .build()).namespaceToken()).isNotBlank();
    }

    private CaffeineCacheBackend backend(
        long maximumSize, Duration ttl, Duration expireAfterAccess) {
        return new CaffeineCacheBackend(
            settings(maximumSize, ttl, expireAfterAccess), ticker);
    }

    private CaffeineCacheSettings settings(
        long maximumSize, Duration ttl, Duration expireAfterAccess) {
        return CaffeineCacheSettings.builder()
            .maximumSize(maximumSize)
            .defaultTtl(ttl)
            .expireAfterAccess(expireAfterAccess)
            .recordStats(true)
            .build();
    }

    private static final class MutableTicker implements Ticker {
        private final AtomicLong nanos = new AtomicLong();

        @Override
        public long read() {
            return nanos.get();
        }

        void advance(Duration duration) {
            nanos.addAndGet(duration.toNanos());
        }
    }
}
