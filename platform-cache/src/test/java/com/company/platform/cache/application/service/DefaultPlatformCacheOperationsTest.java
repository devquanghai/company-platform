package com.company.platform.cache.application.service;

import com.company.platform.cache.adapter.caffeine.CaffeineCacheBackend;
import com.company.platform.cache.adapter.caffeine.CaffeineCacheSettings;
import com.company.platform.cache.api.model.CacheType;
import com.company.platform.cache.application.port.out.CacheBackend;
import com.company.platform.cache.application.port.out.BackendCacheEntry;
import com.company.platform.cache.application.port.out.BackendClearResult;
import com.company.platform.cache.application.port.out.BackendUpdateResult;
import com.company.platform.cache.application.resolver.CacheDefinitionRegistry;
import com.company.platform.cache.application.resolver.DefaultCacheBackendRegistry;
import com.company.platform.cache.consistency.SingleFlightCoordinator;
import com.company.platform.cache.domain.model.CacheResultStatus;
import com.company.platform.cache.domain.model.CacheTier;
import com.company.platform.cache.domain.exception.PlatformCacheOperationException;
import com.company.platform.cache.domain.policy.CacheFailurePolicy;
import com.company.platform.cache.domain.result.OptimisticUpdateStatus;
import com.company.platform.cache.observability.event.CacheOperationEvent;
import com.company.platform.cache.support.CacheTestFixtures;
import com.company.platform.cache.support.DefaultCacheKeyEncoder;
import com.company.platform.cache.support.PlatformCachePropertiesValidator;
import com.company.platform.core.json.JsonMapperHelper;
import com.company.platform.core.time.TimeProvider;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Optional;
import java.util.function.UnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class DefaultPlatformCacheOperationsTest {

    @Test
    void supportsCrudBulkClearAndResultObservability() {
        Harness harness = harness(false);
        DefaultPlatformCacheOperations operations = harness.operations;

        assertThat(operations.getResult("users", "one", String.class).getStatus())
            .isEqualTo(CacheResultStatus.MISS);
        operations.put("users", "one", "first");
        assertThat(operations.get("users", "one", String.class)).contains("first");
        assertThat(operations.get("users", "one", CacheType.of(String.class))).contains("first");
        assertThat(operations.exists("users", "one")).isTrue();
        assertThat(operations.putIfAbsent("users", "one", "ignored")).isFalse();
        assertThat(operations.putIfAbsent("users", "two", "second")).isTrue();

        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("three", "third");
        entries.put("four", "fourth");
        operations.putAll("users", entries);
        assertThat(operations.getAll(
            "users", List.of("one", "missing", "four"), String.class))
            .containsExactlyInAnyOrderEntriesOf(Map.of("one", "first", "four", "fourth"));
        assertThat(operations.evict("users", "two")).isTrue();
        assertThat(operations.clear("users").isSuccess()).isTrue();
        assertThat(harness.events).isNotEmpty();
        assertThat(harness.metrics).hasSameSizeAs(harness.events);
    }

    @Test
    void loadsOnceCachesNullAndEnforcesMaximumSize() {
        Harness harness = harness(true);
        AtomicInteger loads = new AtomicInteger();
        assertThat(harness.operations.getOrLoad(
            "users", "key", String.class, () -> {
                loads.incrementAndGet();
                return "loaded";
            })).isEqualTo("loaded");
        assertThat(harness.operations.getOrLoad(
            "users", "key", String.class, () -> "wrong")).isEqualTo("loaded");
        assertThat(loads).hasValue(1);

        assertThat(harness.operations.getOrLoad(
            "users", "null", String.class, () -> null)).isNull();
        assertThat(harness.operations.getResult(
            "users", "null", String.class).getStatus()).isEqualTo(CacheResultStatus.HIT);

        harness.properties.getDefaults().setMaximumEntrySize(2);
        Harness tiny = harness(harness.properties);
        assertThatIllegalArgumentException().isThrownBy(() ->
            tiny.operations.put("users", "large", "long value"))
            .withMessageContaining("too large");
    }

    @Test
    void supportsAtomicAndOptimisticOperations() {
        Harness harness = harness(false);
        var operations = harness.operations;
        assertThat(operations.increment("users", "counter", 2)).isEqualTo(2);
        assertThat(operations.compareAndSet("users", "counter", 2L, 3L)).isTrue();
        assertThat(operations.compareAndDelete("users", "counter", 3L)).isTrue();

        operations.put("users", "number", 4L);
        assertThat(operations.update(
            "users", "number", Long.class, current -> current * 2).getValue())
            .isEqualTo(8L);
        var current = operations.getVersioned("users", "number", Long.class);
        assertThat(current.getValue()).isEqualTo(8L);
        assertThat(operations.updateIfVersion(
            "users", "number", current.getVersion() + 10, 9L).getStatus())
            .isEqualTo(OptimisticUpdateStatus.VERSION_CONFLICT);
        assertThat(operations.updateIfVersion(
            "users", "number", current.getVersion(), 9L).getStatus())
            .isEqualTo(OptimisticUpdateStatus.UPDATED);
        assertThat(operations.computeWithRetry(
            "users", "number", Long.class, 2, value -> value + 1).getStatus())
            .isEqualTo(OptimisticUpdateStatus.UPDATED);
        assertThat(operations.computeWithRetry(
            "users", "missing", Long.class, 2, value -> value).getStatus())
            .isEqualTo(OptimisticUpdateStatus.NOT_FOUND);
        assertThatIllegalArgumentException().isThrownBy(() ->
            operations.computeWithRetry("users", "number", Long.class, 0, value -> value));
    }

    @Test
    void typedFactoryChecksKeysAndDelegates() {
        Harness harness = harness(false);
        DefaultTypedCacheFactory factory = new DefaultTypedCacheFactory(harness.operations);
        var typed = factory.getCache("users", String.class, String.class);
        typed.put("key", "value");
        assertThat(typed.get("key")).contains("value");
        assertThat(typed.exists("key")).isTrue();
        assertThat(typed.putIfAbsent("key", "other")).isFalse();
        assertThat(typed.getOrLoad("missing", () -> "loaded")).isEqualTo("loaded");
        assertThat(typed.evict("key")).isTrue();
        assertThat(typed.clear().isSuccess()).isTrue();
        assertThatNullPointerException().isThrownBy(() ->
            new DefaultTypedCacheFactory(null));
        assertThatNullPointerException().isThrownBy(() ->
            factory.getCache("users", null, String.class));
        assertThatNullPointerException().isThrownBy(() -> typed.get(null));
    }

    @Test
    void failOpenReturnsSafeDefaultsForBackendFailures() {
        var properties = CacheTestFixtures.validProperties();
        properties.getCaches().get("users").setFailurePolicy(CacheFailurePolicy.FAIL_OPEN);
        DefaultPlatformCacheOperations operations =
            harness(properties, new ThrowingBackend()).operations;

        assertThat(operations.getResult("users", "key", String.class).getStatus())
            .isEqualTo(CacheResultStatus.FAILED);
        assertThat(operations.getResult("users", "key", String.class).getFailure())
            .isNotNull();
        assertThat(operations.get("users", "key", String.class)).isEmpty();
        assertThat(operations.get("users", "key", CacheType.of(String.class))).isEmpty();
        assertThat(operations.exists("users", "key")).isFalse();
        operations.put("users", "key", "value");
        assertThat(operations.putIfAbsent("users", "key", "value")).isFalse();
        assertThat(operations.evict("users", "key")).isFalse();
        assertThat(operations.increment("users", "key", 1)).isZero();
        assertThat(operations.compareAndSet("users", "key", "a", "b")).isFalse();
        assertThat(operations.compareAndDelete("users", "key", "a")).isFalse();
        assertThat(operations.clear("users").isSuccess()).isFalse();
        assertThat(operations.update(
            "users", "key", String.class, value -> value).getFailure()).isNotNull();
        assertThat(operations.getVersioned("users", "key", String.class)).isNull();
        assertThat(operations.updateIfVersion(
            "users", "key", 1, "value").getStatus())
            .isEqualTo(OptimisticUpdateStatus.FAILED);
    }

    @Test
    void failClosedWrapsBackendFailuresAndNullPolicyIsEnforced() {
        var properties = CacheTestFixtures.validProperties();
        properties.getCaches().get("users").setFailurePolicy(CacheFailurePolicy.FAIL_CLOSED);
        DefaultPlatformCacheOperations operations =
            harness(properties, new ThrowingBackend()).operations;
        assertThat(org.assertj.core.api.Assertions.catchThrowable(() ->
            operations.getResult("users", "key", String.class)))
            .isInstanceOf(PlatformCacheOperationException.class)
            .hasMessageContaining("read failed");
        assertThat(org.assertj.core.api.Assertions.catchThrowable(() ->
            operations.get("users", "key", CacheType.of(String.class))))
            .isInstanceOf(PlatformCacheOperationException.class);
        assertThat(org.assertj.core.api.Assertions.catchThrowable(() ->
            operations.exists("users", "key")))
            .isInstanceOf(PlatformCacheOperationException.class);
        assertThat(org.assertj.core.api.Assertions.catchThrowable(() ->
            operations.put("users", "key", "value")))
            .isInstanceOf(PlatformCacheOperationException.class)
            .hasMessageContaining("mutation");
        assertThat(org.assertj.core.api.Assertions.catchThrowable(() ->
            operations.putIfAbsent("users", "key", "value")))
            .isInstanceOf(PlatformCacheOperationException.class);
        assertThat(org.assertj.core.api.Assertions.catchThrowable(() ->
            operations.clear("users")))
            .isInstanceOf(PlatformCacheOperationException.class)
            .hasMessageContaining("clear");
        assertThat(org.assertj.core.api.Assertions.catchThrowable(() ->
            operations.update("users", "key", String.class, value -> value)))
            .isInstanceOf(PlatformCacheOperationException.class)
            .hasMessageContaining("Atomic");
        assertThat(org.assertj.core.api.Assertions.catchThrowable(() ->
            operations.updateIfVersion("users", "key", 1, "value")))
            .isInstanceOf(PlatformCacheOperationException.class)
            .hasMessageContaining("Optimistic");

        assertThatIllegalArgumentException().isThrownBy(() ->
            harness(false).operations.put("users", "null", null))
            .withMessageContaining("Null values are disabled");
        assertThatNullPointerException().isThrownBy(() ->
            harness(false).operations.get("users", "key", (CacheType<String>) null));
        assertThatNullPointerException().isThrownBy(() ->
            harness(false).operations.getAll("users", null, String.class));
        assertThatNullPointerException().isThrownBy(() ->
            harness(false).operations.putAll("users", null));
    }

    @Test
    void reportsStaleAndFallbackTiersAndOptimisticNotFound() {
        var properties = CacheTestFixtures.validProperties();
        BackendCacheEntry stale = new BackendCacheEntry(
            "old", 1, Duration.ofSeconds(5), true, CacheTier.FALLBACK);
        var staleOperations = harness(
            properties, new StaticBackend(Optional.of(stale))).operations;
        assertThat(staleOperations.getResult(
            "users", "key", String.class).getStatus())
            .isEqualTo(CacheResultStatus.HIT_STALE);
        assertThat(staleOperations.get("users", "key", String.class)).isEmpty();
        assertThat(staleOperations.get(
            "users", "key", CacheType.of(String.class))).isEmpty();

        BackendCacheEntry fallback = new BackendCacheEntry(
            "fallback", 1, Duration.ofSeconds(5), false, CacheTier.FALLBACK);
        var fallbackOperations = harness(
            CacheTestFixtures.validProperties(),
            new StaticBackend(Optional.of(fallback))).operations;
        assertThat(fallbackOperations.getResult(
            "users", "key", String.class).getStatus())
            .isEqualTo(CacheResultStatus.HIT_FALLBACK);
        assertThat(fallbackOperations.get("users", "key", String.class))
            .contains("fallback");

        assertThat(harness(false).operations.updateIfVersion(
            "users", "missing", 1, "value").getStatus())
            .isEqualTo(OptimisticUpdateStatus.NOT_FOUND);
        assertThat(new StaticBackend(Optional.empty()).entry).isEmpty();

        var notFoundAtomic = harness(
            CacheTestFixtures.validProperties(),
            new StaticBackend(Optional.empty())).operations.update(
                "users", "missing", String.class, value -> value);
        assertThat(notFoundAtomic.isUpdated()).isFalse();
        assertThat(notFoundAtomic.getValue()).isNull();
    }

    @Test
    void appliesNegativeTtlAndJitterAndSkipsNullWithoutNegativeCaching() {
        var properties = CacheTestFixtures.validProperties();
        properties.getCaches().get("users").getNegativeCache().setEnabled(true);
        properties.getCaches().get("users").getTtlJitter().setEnabled(true);
        properties.getCaches().get("users").getTtlJitter().setPercentage(10);
        Harness negative = harness(properties);
        assertThat(negative.operations.getOrLoad(
            "users", "none", String.class, () -> null)).isNull();
        assertThat(negative.operations.getResult(
            "users", "none", String.class).getStatus()).isEqualTo(CacheResultStatus.HIT);

        AtomicInteger loads = new AtomicInteger();
        Harness noNegative = harness(false);
        noNegative.operations.getOrLoad("users", "none", String.class, () -> {
            loads.incrementAndGet();
            return null;
        });
        noNegative.operations.getOrLoad("users", "none", String.class, () -> {
            loads.incrementAndGet();
            return null;
        });
        assertThat(loads).hasValue(2);
        assertThat(negative.operations.get(
            "users", "none", CacheType.of(String.class))).isEmpty();
    }

    @Test
    void fallsBackNamespaceOnlyForFailOpenAndExhaustsOptimisticConflicts() {
        var failOpen = CacheTestFixtures.validProperties();
        failOpen.getCaches().get("users").setFailurePolicy(CacheFailurePolicy.FAIL_OPEN);
        DefaultPlatformCacheOperations fallbackNamespace =
            harness(failOpen, new NamespaceFailingBackend()).operations;
        assertThat(fallbackNamespace.getResult(
            "users", "key", String.class).getStatus()).isEqualTo(CacheResultStatus.FAILED);

        var failClosed = CacheTestFixtures.validProperties();
        failClosed.getCaches().get("users").setFailurePolicy(CacheFailurePolicy.FAIL_CLOSED);
        assertThat(org.assertj.core.api.Assertions.catchThrowable(() ->
            harness(failClosed, new NamespaceFailingBackend()).operations
                .getResult("users", "key", String.class)))
            .isInstanceOf(PlatformCacheOperationException.class)
            .hasMessageContaining("namespace");

        BackendCacheEntry current = new BackendCacheEntry(
            1L, 1, Duration.ofMinutes(1));
        DefaultPlatformCacheOperations conflicts = harness(
            CacheTestFixtures.validProperties(),
            new ConflictBackend(Optional.of(current))).operations;
        assertThat(conflicts.computeWithRetry(
            "users", "key", Long.class, 2, value -> value + 1).getStatus())
            .isEqualTo(OptimisticUpdateStatus.VERSION_CONFLICT);
    }

    @Test
    void convertsStructuredBackendValuesAndRecognizesMapNullMarker() {
        Map<String, Object> raw = Map.of("name", "Ada");
        DefaultPlatformCacheOperations converting = harness(
            CacheTestFixtures.validProperties(),
            new StaticBackend(Optional.of(new BackendCacheEntry(
                raw, 1, Duration.ofMinutes(1))))).operations;
        assertThat(converting.get("users", "key", TestValue.class).orElseThrow().getName())
            .isEqualTo("Ada");

        DefaultPlatformCacheOperations nullMarker = harness(
            CacheTestFixtures.validProperties(),
            new StaticBackend(Optional.of(new BackendCacheEntry(
                Map.of("cachedNull", true), 1, Duration.ofMinutes(1))))).operations;
        assertThat(nullMarker.getResult("users", "key", String.class).getValue()).isNull();
    }

    private Harness harness(boolean cacheNulls) {
        var properties = CacheTestFixtures.validProperties();
        properties.getCaches().get("users").setCacheNullValues(cacheNulls);
        return harness(properties);
    }

    private Harness harness(
        com.company.platform.cache.autoconfigure.properties.PlatformCacheProperties properties
    ) {
        CaffeineCacheBackend backend = new CaffeineCacheBackend(
            CaffeineCacheSettings.builder()
                .maximumSize(100)
                .defaultTtl(Duration.ofMinutes(10))
                .build());
        return harness(properties, backend);
    }

    private Harness harness(
        com.company.platform.cache.autoconfigure.properties.PlatformCacheProperties properties,
        CacheBackend backend
    ) {
        CacheDefinitionRegistry definitions = new CacheDefinitionRegistry(
            properties, new PlatformCachePropertiesValidator(properties));
        JsonMapperHelper json = new JsonMapperHelper(JsonMapper.builder().build());
        List<CacheOperationEvent> events = new ArrayList<>();
        List<CacheOperationEvent> metrics = new ArrayList<>();
        DefaultPlatformCacheOperations operations = new DefaultPlatformCacheOperations(
            definitions,
            new DefaultCacheBackendRegistry(Map.of("users", backend)),
            new DefaultCacheKeyEncoder(json),
            json,
            new SingleFlightCoordinator(),
            events::add,
            new FixedTimeProvider(),
            metrics::add);
        return new Harness(properties, operations, events, metrics);
    }

    private static final class Harness {
        private final com.company.platform.cache.autoconfigure.properties.PlatformCacheProperties properties;
        private final DefaultPlatformCacheOperations operations;
        private final List<CacheOperationEvent> events;
        private final List<CacheOperationEvent> metrics;

        private Harness(
            com.company.platform.cache.autoconfigure.properties.PlatformCacheProperties properties,
            DefaultPlatformCacheOperations operations,
            List<CacheOperationEvent> events,
            List<CacheOperationEvent> metrics
        ) {
            this.properties = properties;
            this.operations = operations;
            this.events = events;
            this.metrics = metrics;
        }
    }

    private static final class FixedTimeProvider implements TimeProvider {
        private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
        @Override public Instant nowInstant() { return NOW; }
        @Override public OffsetDateTime now() { return NOW.atOffset(ZoneOffset.UTC); }
        @Override public OffsetDateTime now(ZoneId zoneId) { return NOW.atZone(zoneId).toOffsetDateTime(); }
        @Override public ZoneId getDefaultZone() { return ZoneOffset.UTC; }
    }

    private static class ThrowingBackend implements CacheBackend {
        @Override public Optional<BackendCacheEntry> get(String key) { throw failure(); }
        @Override public void put(String key, Object value, Duration ttl) { throw failure(); }
        @Override public boolean putIfAbsent(String key, Object value, Duration ttl) { throw failure(); }
        @Override public boolean evict(String key) { throw failure(); }
        @Override public BackendClearResult clear() { throw failure(); }
        @Override public String namespaceToken() { return "abcdefghijklmnop"; }
        @Override public long estimatedSize() { throw failure(); }
        @Override public long increment(String key, long delta, Duration ttl) { throw failure(); }
        @Override public boolean compareAndSet(String key, Object expectedValue, Object newValue) { throw failure(); }
        @Override public boolean compareAndDelete(String key, Object expectedValue) { throw failure(); }
        @Override public BackendUpdateResult updateIfVersion(String key, long expectedVersion, Object newValue) { throw failure(); }
        @Override public BackendUpdateResult compute(String key, UnaryOperator<Object> updater) { throw failure(); }
        private IllegalStateException failure() { return new IllegalStateException("backend"); }
    }

    private static class StaticBackend implements CacheBackend {
        private final Optional<BackendCacheEntry> entry;
        private StaticBackend(Optional<BackendCacheEntry> entry) { this.entry = entry; }
        @Override public Optional<BackendCacheEntry> get(String key) { return entry; }
        @Override public void put(String key, Object value, Duration ttl) { }
        @Override public boolean putIfAbsent(String key, Object value, Duration ttl) { return false; }
        @Override public boolean evict(String key) { return false; }
        @Override public BackendClearResult clear() { return new BackendClearResult("TEST", "a", "b", null); }
        @Override public String namespaceToken() { return "abcdefghijklmnop"; }
        @Override public long estimatedSize() { return 1; }
        @Override public long increment(String key, long delta, Duration ttl) { return 0; }
        @Override public boolean compareAndSet(String key, Object expectedValue, Object newValue) { return false; }
        @Override public boolean compareAndDelete(String key, Object expectedValue) { return false; }
        @Override public BackendUpdateResult updateIfVersion(String key, long expectedVersion, Object newValue) {
            return new BackendUpdateResult(BackendUpdateResult.Status.NOT_FOUND, null);
        }
        @Override public BackendUpdateResult compute(String key, UnaryOperator<Object> updater) {
            return new BackendUpdateResult(BackendUpdateResult.Status.NOT_FOUND, null);
        }
    }

    private static final class NamespaceFailingBackend extends ThrowingBackend {
        @Override public String namespaceToken() {
            throw new IllegalStateException("namespace");
        }
    }

    private static final class ConflictBackend extends StaticBackend {
        private ConflictBackend(Optional<BackendCacheEntry> entry) { super(entry); }
        @Override public BackendUpdateResult updateIfVersion(
            String key, long expectedVersion, Object newValue
        ) {
            return new BackendUpdateResult(
                BackendUpdateResult.Status.VERSION_CONFLICT,
                new BackendCacheEntry(1L, expectedVersion, Duration.ofMinutes(1)));
        }
    }

    public static final class TestValue {
        private String name;
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }
}
