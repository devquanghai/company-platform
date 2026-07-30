package com.company.platform.cache.consistency;

import com.company.platform.cache.domain.exception.PlatformCacheOperationException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SingleFlightCoordinatorTest {

    @Test
    void executesOneLeaderAndSharesItsResult() throws Exception {
        SingleFlightCoordinator coordinator = new SingleFlightCoordinator();
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<String> leader = executor.submit(() -> coordinator.execute(
                "cache|token|key", Duration.ofSeconds(2), 10, () -> {
                    calls.incrementAndGet();
                    entered.countDown();
                    await(release);
                    return "loaded";
                }));
            entered.await();
            Future<String> follower = executor.submit(() -> coordinator.execute(
                "cache|token|key", Duration.ofSeconds(2), 10, () -> "wrong"));
            Thread.sleep(20);
            release.countDown();

            assertThat(leader.get()).isEqualTo("loaded");
            assertThat(follower.get()).isEqualTo("loaded");
        }
        assertThat(calls).hasValue(1);
        assertThat(coordinator.inFlightCount()).isZero();
    }

    @Test
    void followerTimeoutDoesNotCancelLeader() throws Exception {
        SingleFlightCoordinator coordinator = new SingleFlightCoordinator();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<String> leader = executor.submit(() -> coordinator.execute(
                "same", Duration.ofSeconds(1), 10, () -> {
                    entered.countDown();
                    await(release);
                    return "done";
                }));
            entered.await();
            Future<String> follower = executor.submit(() -> coordinator.execute(
                "same", Duration.ofMillis(5), 10, () -> "never"));

            assertThatThrownBy(follower::get)
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(PlatformCacheOperationException.class);
            assertThat(leader.isCancelled()).isFalse();
            release.countDown();
            assertThat(leader.get()).isEqualTo("done");
        }
    }

    @Test
    void propagatesLeaderFailuresAndCleansUp() {
        SingleFlightCoordinator coordinator = new SingleFlightCoordinator();
        IllegalStateException failure = new IllegalStateException("loader");
        assertThatThrownBy(() -> coordinator.execute(
            "key", Duration.ofSeconds(1), 1, () -> {
                throw failure;
            })).isSameAs(failure);
        assertThat(coordinator.inFlightCount()).isZero();
    }

    @Test
    void rejectsExcessIndependentInflightLoads() throws Exception {
        SingleFlightCoordinator coordinator = new SingleFlightCoordinator();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            Future<String> first = executor.submit(() -> coordinator.execute(
                "first", Duration.ofSeconds(1), 1, () -> {
                    entered.countDown();
                    await(release);
                    return "one";
                }));
            entered.await();
            assertThatThrownBy(() -> coordinator.execute(
                "second", Duration.ofSeconds(1), 1, () -> "two"))
                .isInstanceOf(PlatformCacheOperationException.class)
                .hasMessageContaining("Maximum");
            release.countDown();
            assertThat(first.get()).isEqualTo("one");
        }
    }

    @Test
    void validatesArguments() {
        SingleFlightCoordinator coordinator = new SingleFlightCoordinator();
        assertThatIllegalArgumentException().isThrownBy(() ->
            coordinator.execute(" ", Duration.ofSeconds(1), 1, () -> "x"));
        assertThatIllegalArgumentException().isThrownBy(() ->
            coordinator.execute("x", Duration.ZERO, 1, () -> "x"));
        assertThatIllegalArgumentException().isThrownBy(() ->
            coordinator.execute("x", Duration.ofSeconds(1), 0, () -> "x"));
        assertThatNullPointerException().isThrownBy(() ->
            coordinator.execute("x", Duration.ofSeconds(1), 1, null));
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }
}
