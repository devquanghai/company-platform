package com.company.platform.cache.observability;

import com.company.platform.cache.adapter.lock.FailClosedDistributedLockOperations;
import com.company.platform.cache.adapter.noop.NoOpCacheBackend;
import com.company.platform.cache.api.lock.LockOptions;
import com.company.platform.cache.application.resolver.DefaultCacheBackendRegistry;
import com.company.platform.cache.domain.exception.PlatformCacheOperationException;
import com.company.platform.cache.domain.model.CacheProviderType;
import com.company.platform.cache.domain.model.CacheResultStatus;
import com.company.platform.cache.domain.model.CacheTier;
import com.company.platform.cache.observability.event.CacheOperationEvent;
import com.company.platform.cache.observability.event.SpringCacheEventPublisher;
import com.company.platform.cache.observability.health.PlatformCacheHealthIndicator;
import com.company.platform.cache.observability.metrics.MicrometerCacheMetricsRecorder;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CacheObservabilityAndLockTest {

    @Test
    void recordsLowCardinalityMetricsAndPublishesEvent() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MicrometerCacheMetricsRecorder recorder = new MicrometerCacheMetricsRecorder(registry);
        CacheOperationEvent event = CacheOperationEvent.builder()
            .timestamp(OffsetDateTime.parse("2026-01-01T00:00:00Z"))
            .cacheName("users")
            .operation("GET")
            .provider(CacheProviderType.CAFFEINE)
            .outcome(CacheResultStatus.HIT)
            .tier(CacheTier.L1)
            .duration(Duration.ofMillis(3))
            .build();
        recorder.record(event);
        assertThat(registry.get("platform.cache.operations").counter().count()).isEqualTo(1);
        assertThat(registry.get("platform.cache.operation.duration").timer().count()).isEqualTo(1);

        AtomicReference<Object> published = new AtomicReference<>();
        ApplicationEventPublisher publisher = published::set;
        SpringCacheEventPublisher events = new SpringCacheEventPublisher(publisher);
        events.publish(event);
        assertThat(published).hasValue(event);
        assertThatNullPointerException().isThrownBy(() -> events.publish(null));
        assertThatNullPointerException().isThrownBy(() -> new SpringCacheEventPublisher(null));
        assertThatNullPointerException().isThrownBy(() -> new MicrometerCacheMetricsRecorder(null));
    }

    @Test
    void reportsBackendHealthWithoutSensitiveFailureDetails() {
        PlatformCacheHealthIndicator health = new PlatformCacheHealthIndicator(
            new DefaultCacheBackendRegistry(Map.of("users", new NoOpCacheBackend())));
        assertThat(health.health().getStatus().getCode()).isEqualTo("UP");
        assertThat(health.health().getDetails()).containsKey("caches");
        assertThatNullPointerException().isThrownBy(() ->
            new PlatformCacheHealthIndicator(null));
    }

    @Test
    void distributedLockAlwaysFailsClosed() {
        FailClosedDistributedLockOperations locks =
            new FailClosedDistributedLockOperations();
        LockOptions options = LockOptions.builder().build();
        assertThat(locks.tryLock("settlement", options)).isEmpty();
        AtomicReference<Boolean> executed = new AtomicReference<>(false);
        assertThatThrownBy(() -> locks.executeWithLock(
            "settlement", options, () -> {
                executed.set(true);
                return "unsafe";
            }))
            .isInstanceOf(PlatformCacheOperationException.class)
            .hasMessageContaining("unavailable");
        assertThat(executed).hasValue(false);
        assertThatThrownBy(() -> locks.tryLock(" ", options))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatNullPointerException().isThrownBy(() ->
            locks.tryLock("lock", null));
        assertThatNullPointerException().isThrownBy(() ->
            locks.executeWithLock("lock", options, null));
    }
}
