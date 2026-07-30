package com.company.platform.cache.adapter.noop;

import com.company.platform.cache.application.port.out.BackendClearResult;
import com.company.platform.cache.application.port.out.BackendUpdateResult;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class NoOpCacheBackendTest {

    private final NoOpCacheBackend backend = new NoOpCacheBackend();

    @Test
    void alwaysMissesAndNeverRetainsMutations() {
        backend.put("key", "value", Duration.ofMinutes(1));

        assertThat(backend.get("key")).isEmpty();
        assertThat(backend.putIfAbsent(
            "key", "value", Duration.ofMinutes(1))).isFalse();
        assertThat(backend.evict("key")).isFalse();
        assertThat(backend.estimatedSize()).isZero();
        assertThat(backend.increment(
            "counter", 10L, Duration.ofMinutes(1))).isZero();
        assertThat(backend.compareAndSet("key", null, "value")).isFalse();
        assertThat(backend.compareAndDelete("key", null)).isFalse();
    }

    @Test
    void returnsNotFoundForUpdatesWithoutExecutingUpdater() {
        BackendUpdateResult optimistic =
            backend.updateIfVersion("key", 1L, "value");
        assertThat(optimistic.getStatus())
            .isEqualTo(BackendUpdateResult.Status.NOT_FOUND);
        assertThat(optimistic.getEntry()).isNull();

        BackendUpdateResult computed = backend.compute(
            "key", value -> {
                throw new AssertionError("must not execute");
            });
        assertThat(computed.getStatus())
            .isEqualTo(BackendUpdateResult.Status.NOT_FOUND);
    }

    @Test
    void rotatesNamespaceOnLogicalClear() {
        String previous = backend.namespaceToken();
        BackendClearResult result = backend.clear();

        assertThat(result.getStrategy()).isEqualTo("NOOP");
        assertThat(result.getPreviousNamespaceToken()).isEqualTo(previous);
        assertThat(result.getCurrentNamespaceToken())
            .isEqualTo(backend.namespaceToken())
            .isNotEqualTo(previous);
        assertThat(result.getExactDeletedCount()).isZero();
    }

    @Test
    void validatesInputsEvenThoughStorageIsDisabled() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> backend.get(""));
        assertThatIllegalArgumentException()
            .isThrownBy(() -> backend.evict(null));
        assertThatNullPointerException()
            .isThrownBy(() -> backend.put("key", null, Duration.ofSeconds(1)));
        assertThatIllegalArgumentException()
            .isThrownBy(() -> backend.put("key", "value", Duration.ZERO));
        assertThatIllegalArgumentException()
            .isThrownBy(() -> backend.increment("key", 1L, null));
        assertThatNullPointerException()
            .isThrownBy(() -> backend.compareAndSet("key", null, null));
        assertThatNullPointerException()
            .isThrownBy(() -> backend.updateIfVersion("key", 1L, null));
        assertThatNullPointerException()
            .isThrownBy(() -> backend.compute("key", null));
    }
}
