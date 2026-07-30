package com.company.platform.cache.resilience;

import com.company.platform.cache.adapter.caffeine.CaffeineCacheBackend;
import com.company.platform.cache.adapter.caffeine.CaffeineCacheSettings;
import com.company.platform.cache.application.port.out.BackendCacheEntry;
import com.company.platform.cache.autoconfigure.properties.ResilienceProperties;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CacheResilienceTest {

    @Test
    void retriesOnlyTransientIdempotentFailures() {
        ResilienceProperties properties = properties();
        CacheResilienceExecutor executor = new CacheResilienceExecutor("redis", properties);
        AtomicInteger calls = new AtomicInteger();
        assertThat(executor.execute(() -> {
            if (calls.incrementAndGet() == 1) {
                throw new RedisConnectionFailureException("down");
            }
            return "ok";
        }, true)).isEqualTo("ok");
        assertThat(calls).hasValue(2);
        assertThat(executor.circuitState()).isEqualTo("CLOSED");

        AtomicInteger nonIdempotent = new AtomicInteger();
        assertThatThrownBy(() -> executor.execute(() -> {
            nonIdempotent.incrementAndGet();
            throw new RedisConnectionFailureException("down");
        }, false)).isInstanceOf(RedisConnectionFailureException.class);
        assertThat(nonIdempotent).hasValue(1);
    }

    @Test
    void disabledExecutorDelegatesAndValidatesArguments() {
        ResilienceProperties properties = properties();
        properties.setEnabled(false);
        CacheResilienceExecutor executor = new CacheResilienceExecutor("local", properties);
        AtomicInteger calls = new AtomicInteger();
        executor.execute(calls::incrementAndGet, true);
        assertThat(calls).hasValue(1);
        assertThatNullPointerException().isThrownBy(() ->
            new CacheResilienceExecutor(null, properties));
        assertThatNullPointerException().isThrownBy(() ->
            new CacheResilienceExecutor("x", null));
        assertThatNullPointerException().isThrownBy(() ->
            executor.execute((java.util.function.Supplier<Object>) null, true));
    }

    @Test
    void supportsIndividuallyDisabledPoliciesAndDoesNotRetryProgrammingErrors() {
        ResilienceProperties properties = properties();
        properties.getRetry().setEnabled(false);
        properties.getCircuitBreaker().setEnabled(false);
        properties.getBulkhead().setEnabled(false);
        CacheResilienceExecutor executor =
            new CacheResilienceExecutor("selective", properties);
        assertThat(executor.execute(() -> "ok", true)).isEqualTo("ok");

        ResilienceProperties enabled = properties();
        AtomicInteger calls = new AtomicInteger();
        assertThatThrownBy(() -> new CacheResilienceExecutor("programming", enabled)
            .execute(() -> {
                calls.incrementAndGet();
                throw new IllegalArgumentException("programming");
            }, true)).isInstanceOf(IllegalArgumentException.class);
        assertThat(calls).hasValue(1);

        AtomicInteger nested = new AtomicInteger();
        assertThat(new CacheResilienceExecutor("nested", enabled).execute(() -> {
            if (nested.incrementAndGet() == 1) {
                throw new IllegalStateException(
                    new java.net.ConnectException("connection"));
            }
            return "recovered";
        }, true)).isEqualTo("recovered");
        assertThat(nested).hasValue(2);
    }

    @Test
    void resilientBackendDelegatesCompleteProviderContract() {
        CaffeineCacheBackend delegate = new CaffeineCacheBackend(
            CaffeineCacheSettings.builder()
                .maximumSize(100)
                .defaultTtl(Duration.ofMinutes(10))
                .build());
        ResilientCacheBackend backend = new ResilientCacheBackend(
            delegate, new CacheResilienceExecutor("local", disabled()));

        backend.put("key", "v1", Duration.ofMinutes(1));
        backend.putEntry("entry", new BackendCacheEntry(
            "value", 1, Duration.ofSeconds(30)), Duration.ofSeconds(30));
        assertThat(backend.get("key")).isPresent();
        assertThat(backend.putIfAbsent("key", "ignored", Duration.ofMinutes(1))).isFalse();
        assertThat(backend.evict("entry")).isTrue();
        assertThat(backend.increment("count", 2, Duration.ofMinutes(1))).isEqualTo(2);
        assertThat(backend.compareAndSet("key", "v1", "v2")).isTrue();
        assertThat(backend.updateIfVersion("key", 2, "v3").getEntry().getValue())
            .isEqualTo("v3");
        assertThat(backend.compute("key", value -> value + "!").getEntry().getValue())
            .isEqualTo("v3!");
        assertThat(backend.compareAndDelete("key", "v3!")).isTrue();
        assertThat(backend.namespaceToken()).isNotBlank();
        assertThat(backend.estimatedSize()).isEqualTo(1);
        assertThat(backend.clear().getStrategy()).isEqualTo("LOCAL_INVALIDATE_ALL");

        assertThatNullPointerException().isThrownBy(() ->
            new ResilientCacheBackend(null, new CacheResilienceExecutor("x", disabled())));
        assertThatNullPointerException().isThrownBy(() ->
            new ResilientCacheBackend(delegate, null));
    }

    private ResilienceProperties properties() {
        ResilienceProperties properties = new ResilienceProperties();
        properties.getRetry().setWaitDuration(Duration.ofMillis(1));
        properties.getCircuitBreaker().setMinimumNumberOfCalls(10);
        return properties;
    }

    private ResilienceProperties disabled() {
        ResilienceProperties properties = properties();
        properties.setEnabled(false);
        return properties;
    }
}
