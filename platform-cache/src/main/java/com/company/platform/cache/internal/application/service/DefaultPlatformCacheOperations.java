package com.company.platform.cache.internal.application.service;

import com.company.platform.cache.api.model.CacheType;
import com.company.platform.cache.api.operation.AtomicCacheOperations;
import com.company.platform.cache.api.operation.OptimisticCacheOperations;
import com.company.platform.cache.api.operation.PlatformCacheOperations;
import com.company.platform.cache.internal.application.port.out.BackendCacheEntry;
import com.company.platform.cache.internal.application.port.out.BackendClearResult;
import com.company.platform.cache.internal.application.port.out.BackendUpdateResult;
import com.company.platform.cache.internal.application.port.out.CacheBackend;
import com.company.platform.cache.internal.application.port.out.CacheBackendRegistry;
import com.company.platform.cache.internal.application.port.out.CacheKeyEncoder;
import com.company.platform.cache.internal.application.resolver.CacheDefinitionRegistry;
import com.company.platform.cache.internal.application.resolver.NamedCacheDefinition;
import com.company.platform.cache.domain.exception.PlatformCacheOperationException;
import com.company.platform.cache.domain.model.CacheFailure;
import com.company.platform.cache.domain.model.CacheResultStatus;
import com.company.platform.cache.domain.model.CacheTier;
import com.company.platform.cache.domain.policy.CacheFailurePolicy;
import com.company.platform.cache.domain.result.AtomicUpdateResult;
import com.company.platform.cache.domain.result.CacheClearResult;
import com.company.platform.cache.domain.result.CacheResult;
import com.company.platform.cache.domain.result.OptimisticUpdateResult;
import com.company.platform.cache.domain.result.OptimisticUpdateStatus;
import com.company.platform.cache.domain.result.VersionedValue;
import com.company.platform.cache.internal.consistency.SingleFlightCoordinator;
import com.company.platform.cache.internal.consistency.LocalNamespaceTokenProvider;
import com.company.platform.cache.internal.consistency.CacheKeyMutex;
import com.company.platform.cache.observability.event.CacheEventPublisher;
import com.company.platform.cache.observability.event.CacheOperationEvent;
import com.company.platform.cache.observability.metrics.CacheMetricsRecorder;
import com.company.platform.core.time.TimeProvider;
import com.company.platform.core.json.JsonMapperHelper;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JavaType;

import java.time.Duration;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
public final class DefaultPlatformCacheOperations
    implements PlatformCacheOperations, AtomicCacheOperations, OptimisticCacheOperations {

    private static final String OPERATION_ERROR = "CACHE.OPERATION.FAILED";
    private static final CachedNullValue NULL_VALUE = new CachedNullValue();

    private final CacheDefinitionRegistry definitions;
    private final CacheBackendRegistry backends;
    private final CacheKeyEncoder keys;
    private final JsonMapperHelper json;
    private final SingleFlightCoordinator singleFlight;
    private final CacheEventPublisher events;
    private final TimeProvider time;
    private final CacheMetricsRecorder metrics;
    private final ConcurrentMap<String, AtomicLong> entryEpochs =
        new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> lastNamespaceTokens =
        new ConcurrentHashMap<>();
    private final LocalNamespaceTokenProvider failOpenNamespaces =
        new LocalNamespaceTokenProvider();
    private final CacheKeyMutex keyMutex = new CacheKeyMutex();

    public DefaultPlatformCacheOperations(
        CacheDefinitionRegistry definitions,
        CacheBackendRegistry backends,
        CacheKeyEncoder keys,
        JsonMapperHelper json,
        SingleFlightCoordinator singleFlight,
        CacheEventPublisher events,
        TimeProvider time,
        CacheMetricsRecorder metrics
    ) {
        this.definitions = Objects.requireNonNull(definitions, "definitions");
        this.backends = Objects.requireNonNull(backends, "backends");
        this.keys = Objects.requireNonNull(keys, "keys");
        this.json = Objects.requireNonNull(json, "json");
        this.singleFlight = Objects.requireNonNull(singleFlight, "singleFlight");
        this.events = Objects.requireNonNull(events, "events");
        this.time = Objects.requireNonNull(time, "time");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
    }

    @Override
    public <K, V> Optional<V> get(String cacheName, K key, Class<V> valueType) {
        CacheResult<V> result = getResult(cacheName, key, valueType);
        return (result.getStatus() == CacheResultStatus.HIT
            || result.getStatus() == CacheResultStatus.HIT_FALLBACK)
            ? Optional.ofNullable(result.getValue())
            : Optional.empty();
    }

    @Override
    public <K, V> Optional<V> get(
        String cacheName, K key, CacheType<V> valueType
    ) {
        Objects.requireNonNull(valueType, "valueType must not be null");
        Lookup lookup = lookup(cacheName, key);
        try {
            return lookup.backend().get(lookup.encodedKey())
                .filter(entry -> !entry.isStale())
                .map(BackendCacheEntry::getValue)
                .filter(value -> !isNullValue(value))
                .map(value -> convert(value, valueType));
        } catch (RuntimeException exception) {
            return onReadFailure(lookup.definition(), exception);
        }
    }

    @Override
    public <K, V> V getOrLoad(
        String cacheName, K key, Class<V> valueType, Supplier<V> loader
    ) {
        Objects.requireNonNull(loader, "loader must not be null");
        Lookup lookup = lookup(cacheName, key);
        Optional<BackendCacheEntry> cached;
        try {
            cached = lookup.backend().get(lookup.encodedKey());
        } catch (RuntimeException failure) {
            if (!failOpen(lookup.definition())) {
                throw operationFailure("Cache read failed", failure);
            }
            cached = Optional.empty();
        }
        if (cached.isPresent() && !cached.get().isStale()) {
            Object stored = cached.get().getValue();
            return isNullValue(stored) ? null : convert(stored, valueType);
        }
        NamedCacheDefinition definition = lookup.definition();
        long snappedEpoch = entryEpoch(lookup.encodedKey());
        return singleFlight.execute(
            definition.getName() + "|" + snappedEpoch + "|" + lookup.encodedKey(),
            definition.getProperties().getStampede().getWaitTimeout(),
            definition.getProperties().getStampede().getMaximumInflight(),
            () -> {
                Optional<BackendCacheEntry> rechecked;
                try {
                    rechecked = lookup.backend().get(lookup.encodedKey());
                } catch (RuntimeException failure) {
                    if (!failOpen(definition)) {
                        throw operationFailure(
                            "Cache read failed during single-flight recheck",
                            failure);
                    }
                    rechecked = Optional.empty();
                }
                if (rechecked.isPresent() && !rechecked.get().isStale()) {
                    Object value = rechecked.get().getValue();
                    return isNullValue(value)
                        ? null : convert(value, valueType);
                }
                V loaded = loader.get();
                if (loaded != null || definition.isCacheNullValues()
                    || definition.getProperties().getNegativeCache().isEnabled()) {
                    keyMutex.execute(lookup.encodedKey(), () -> {
                        if (entryEpoch(lookup.encodedKey()) == snappedEpoch) {
                            Object stored = storedValue(definition, loaded);
                            enforceMaximumSize(definition, stored);
                            try {
                                lookup.backend().put(
                                    lookup.encodedKey(), stored,
                                    effectiveTtl(definition, loaded));
                            } catch (RuntimeException failure) {
                                if (!failOpen(definition)) {
                                    throw operationFailure(
                                        "Cache mutation failed", failure);
                                }
                            }
                        }
                    });
                }
                return loaded;
            });
    }

    @Override
    public <K, V> V getOrLoad(
        String cacheName,
        K key,
        CacheType<V> valueType,
        Supplier<V> loader
    ) {
        Objects.requireNonNull(valueType, "valueType must not be null");
        Objects.requireNonNull(loader, "loader must not be null");

        Lookup lookup = lookup(cacheName, key);

        Optional<BackendCacheEntry> cached;

        try {
            cached = lookup.backend().get(lookup.encodedKey());
        } catch (RuntimeException failure) {
            if (!failOpen(lookup.definition())) {
                throw operationFailure("Cache read failed", failure);
            }

            cached = Optional.empty();
        }

        if (cached.isPresent() && !cached.get().isStale()) {
            Object stored = cached.get().getValue();

            return isNullValue(stored)
                ? null
                : convert(stored, valueType);
        }

        NamedCacheDefinition definition = lookup.definition();

        long snappedEpoch = entryEpoch(lookup.encodedKey());

        return singleFlight.execute(
            definition.getName()
                + "|"
                + snappedEpoch
                + "|"
                + lookup.encodedKey(),

            definition.getProperties()
                .getStampede()
                .getWaitTimeout(),

            definition.getProperties()
                .getStampede()
                .getMaximumInflight(),

            () -> {

                Optional<BackendCacheEntry> rechecked;

                try {
                    rechecked = lookup.backend().get(
                        lookup.encodedKey()
                    );
                } catch (RuntimeException failure) {
                    if (!failOpen(definition)) {
                        throw operationFailure(
                            "Cache read failed during single-flight recheck",
                            failure
                        );
                    }

                    rechecked = Optional.empty();
                }

                if (rechecked.isPresent() && !rechecked.get().isStale()) {
                    Object stored = rechecked.get().getValue();

                    return isNullValue(stored)
                        ? null
                        : convert(stored, valueType);
                }

                V loaded = loader.get();

                boolean shouldCache =
                    loaded != null
                        || definition.isCacheNullValues()
                        || definition.getProperties()
                        .getNegativeCache()
                        .isEnabled();

                if (shouldCache) {
                    keyMutex.execute(
                        lookup.encodedKey(),
                        () -> {
                            if (entryEpoch(lookup.encodedKey()) != snappedEpoch) {
                                return;
                            }

                            Object stored = storedValue(
                                definition,
                                loaded
                            );

                            enforceMaximumSize(
                                definition,
                                stored
                            );

                            try {
                                lookup.backend().put(
                                    lookup.encodedKey(),
                                    stored,
                                    effectiveTtl(
                                        definition,
                                        loaded
                                    )
                                );
                            } catch (RuntimeException failure) {
                                if (!failOpen(definition)) {
                                    throw operationFailure(
                                        "Cache mutation failed",
                                        failure
                                    );
                                }
                            }
                        }
                    );
                }

                return loaded;
            }
        );
    }

    @Override
    public <K, V> CacheResult<V> getResult(
        String cacheName, K key, Class<V> valueType
    ) {
        Objects.requireNonNull(valueType, "valueType must not be null");
        long started = System.nanoTime();
        Lookup lookup = lookup(cacheName, key);
        try {
            Optional<BackendCacheEntry> found = lookup.backend().get(lookup.encodedKey());
            if (found.isEmpty()) {
                return result(lookup.definition(), CacheResultStatus.MISS, null,
                    CacheTier.NONE, false, started, null);
            }
            BackendCacheEntry entry = found.get();
            V value = isNullValue(entry.getValue())
                ? null : convert(entry.getValue(), valueType);
            CacheResultStatus status = entry.isStale()
                ? CacheResultStatus.HIT_STALE
                : entry.getTier() == CacheTier.FALLBACK
                    ? CacheResultStatus.HIT_FALLBACK : CacheResultStatus.HIT;
            return result(lookup.definition(), status, value,
                entry.getTier() == CacheTier.NONE ? tier(lookup.definition()) : entry.getTier(),
                entry.isStale(), started, null);
        } catch (RuntimeException exception) {
            if (failOpen(lookup.definition())) {
                return result(lookup.definition(), CacheResultStatus.FAILED, null,
                    CacheTier.NONE, false, started,
                    CacheFailure.of(OPERATION_ERROR, "INFRASTRUCTURE", true));
            }
            throw operationFailure("Cache read failed", exception);
        }
    }

    @Override
    public <K, V> void put(String cacheName, K key, V value) {
        Lookup lookup = lookup(cacheName, key);
        Object stored = storedValue(lookup.definition(), value);
        enforceMaximumSize(lookup.definition(), stored);
        keyMutex.execute(lookup.encodedKey(), () -> {
            advanceEntryEpoch(lookup.encodedKey());
            invokeMutation(lookup.definition(), () ->
                lookup.backend().put(
                    lookup.encodedKey(), stored,
                    effectiveTtl(lookup.definition(), value)));
        });
    }

    @Override
    public <K, V> boolean putIfAbsent(String cacheName, K key, V value) {
        Lookup lookup = lookup(cacheName, key);
        Object stored = storedValue(lookup.definition(), value);
        enforceMaximumSize(lookup.definition(), stored);
        return keyMutex.execute(lookup.encodedKey(), () -> {
            advanceEntryEpoch(lookup.encodedKey());
            return invokeMutation(lookup.definition(), () ->
                lookup.backend().putIfAbsent(
                    lookup.encodedKey(), stored,
                    effectiveTtl(lookup.definition(), value)), false);
        });
    }

    @Override
    public <K> boolean exists(String cacheName, K key) {
        Lookup lookup = lookup(cacheName, key);
        try {
            return lookup.backend().get(lookup.encodedKey()).isPresent();
        } catch (RuntimeException exception) {
            return readFailure(lookup.definition(), exception, false);
        }
    }

    @Override
    public <K> boolean evict(String cacheName, K key) {
        Lookup lookup = lookup(cacheName, key);
        return keyMutex.execute(lookup.encodedKey(), () -> {
            advanceEntryEpoch(lookup.encodedKey());
            return invokeMutation(
                lookup.definition(), () -> lookup.backend().evict(
                    lookup.encodedKey()), false);
        });
    }

    @Override
    public CacheClearResult clear(String cacheName) {
        NamedCacheDefinition definition = definitions.requireCache(cacheName);
        CacheBackend backend = backends.require(definition.getName());
        try {
            BackendClearResult result = backend.clear();
            return CacheClearResult.builder()
                .strategy(result.getStrategy())
                .success(true)
                .previousToken(result.getPreviousNamespaceToken())
                .currentToken(result.getCurrentNamespaceToken())
                .deletedCount(result.getExactDeletedCount())
                .build();
        } catch (RuntimeException exception) {
            if (failOpen(definition)) {
                return CacheClearResult.builder()
                    .strategy("FAILED").success(false).build();
            }
            throw operationFailure("Cache clear failed", exception);
        }
    }

    @Override
    public <K, V> Map<K, V> getAll(
        String cacheName, Collection<K> keys, Class<V> valueType
    ) {
        Objects.requireNonNull(keys, "keys must not be null");
        Map<K, V> values = new LinkedHashMap<>();
        for (K key : keys) {
            get(cacheName, key, valueType).ifPresent(value -> values.put(key, value));
        }
        return values;
    }

    @Override
    public <K, V> void putAll(String cacheName, Map<K, V> entries) {
        Objects.requireNonNull(entries, "entries must not be null");
        entries.forEach((key, value) -> put(cacheName, key, value));
    }

    @Override
    public long increment(String cacheName, Object key, long delta) {
        Lookup lookup = lookup(cacheName, key);
        return keyMutex.execute(lookup.encodedKey(), () -> {
            advanceEntryEpoch(lookup.encodedKey());
            return invokeMutation(lookup.definition(), () ->
                lookup.backend().increment(
                    lookup.encodedKey(), delta, lookup.definition().getTtl()), 0L);
        });
    }

    @Override
    public boolean compareAndSet(
        String cacheName, Object key, Object expectedValue, Object newValue
    ) {
        Lookup lookup = lookup(cacheName, key);
        return keyMutex.execute(lookup.encodedKey(), () -> {
            advanceEntryEpoch(lookup.encodedKey());
            return invokeMutation(lookup.definition(), () ->
                lookup.backend().compareAndSet(
                    lookup.encodedKey(), expectedValue,
                    storedValue(lookup.definition(), newValue)), false);
        });
    }

    @Override
    public boolean compareAndDelete(
        String cacheName, Object key, Object expectedValue
    ) {
        Lookup lookup = lookup(cacheName, key);
        return keyMutex.execute(lookup.encodedKey(), () -> {
            advanceEntryEpoch(lookup.encodedKey());
            return invokeMutation(lookup.definition(), () ->
                lookup.backend().compareAndDelete(
                    lookup.encodedKey(), expectedValue), false);
        });
    }

    @Override
    public <T> AtomicUpdateResult<T> update(
        String cacheName, Object key, Class<T> valueType, UnaryOperator<T> updater
    ) {
        Objects.requireNonNull(updater, "updater must not be null");
        Lookup lookup = lookup(cacheName, key);
        return keyMutex.execute(lookup.encodedKey(), () -> {
            advanceEntryEpoch(lookup.encodedKey());
            try {
                BackendUpdateResult result = lookup.backend().compute(
                    lookup.encodedKey(),
                    current -> updater.apply(convert(current, valueType)));
                return AtomicUpdateResult.<T>builder()
                    .updated(result.getStatus() == BackendUpdateResult.Status.UPDATED)
                    .value(entryValue(result.getEntry(), valueType))
                    .build();
            } catch (RuntimeException exception) {
                if (failOpen(lookup.definition())) {
                    return AtomicUpdateResult.<T>builder()
                        .failure(CacheFailure.of(
                            OPERATION_ERROR, "INFRASTRUCTURE", true))
                        .build();
                }
                throw operationFailure("Atomic cache update failed", exception);
            }
        });
    }

    @Override
    public <T> VersionedValue<T> getVersioned(
        String cacheName, Object key, Class<T> valueType
    ) {
        Lookup lookup = lookup(cacheName, key);
        try {
            return lookup.backend().get(lookup.encodedKey())
                .map(entry -> new VersionedValue<>(
                    entry.getVersion(), convert(entry.getValue(), valueType)))
                .orElse(null);
        } catch (RuntimeException exception) {
            return readFailure(lookup.definition(), exception, null);
        }
    }

    @Override
    public <T> OptimisticUpdateResult<T> updateIfVersion(
        String cacheName, Object key, long expectedVersion, T newValue
    ) {
        Lookup lookup = lookup(cacheName, key);
        return keyMutex.execute(lookup.encodedKey(), () -> {
            advanceEntryEpoch(lookup.encodedKey());
            try {
                BackendUpdateResult backend = lookup.backend().updateIfVersion(
                    lookup.encodedKey(), expectedVersion,
                    storedValue(lookup.definition(), newValue));
                return optimisticResult(backend, typeOf(newValue));
            } catch (RuntimeException exception) {
                if (failOpen(lookup.definition())) {
                    return OptimisticUpdateResult.<T>builder()
                        .status(OptimisticUpdateStatus.FAILED)
                        .failure(CacheFailure.of(
                            OPERATION_ERROR, "INFRASTRUCTURE", true))
                        .build();
                }
                throw operationFailure("Optimistic cache update failed", exception);
            }
        });
    }

    @Override
    public <T> OptimisticUpdateResult<T> computeWithRetry(
        String cacheName, Object key, Class<T> valueType,
        int maxAttempts, UnaryOperator<T> updater
    ) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be at least 1");
        }
        Objects.requireNonNull(updater, "updater must not be null");
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            VersionedValue<T> current = getVersioned(cacheName, key, valueType);
            if (current == null) {
                return OptimisticUpdateResult.<T>builder()
                    .status(OptimisticUpdateStatus.NOT_FOUND).build();
            }
            OptimisticUpdateResult<T> result = updateIfVersion(
                cacheName, key, current.getVersion(), updater.apply(current.getValue()));
            if (result.getStatus() != OptimisticUpdateStatus.VERSION_CONFLICT) {
                return result;
            }
        }
        return OptimisticUpdateResult.<T>builder()
            .status(OptimisticUpdateStatus.VERSION_CONFLICT).build();
    }

    private Lookup lookup(String cacheName, Object key) {
        NamedCacheDefinition definition = definitions.requireCache(cacheName);
        CacheBackend backend = backends.require(definition.getName());
        String namespaceToken;
        try {
            namespaceToken = backend.namespaceToken();
            lastNamespaceTokens.put(definition.getName(), namespaceToken);
        } catch (RuntimeException failure) {
            log.error("Cache namespace lookup failed for cache: {}", definition.getName(), failure);
            if (!failOpen(definition)) {
                throw operationFailure("Cache namespace lookup failed", failure);
            }
            namespaceToken = lastNamespaceTokens.computeIfAbsent(
                definition.getName(), failOpenNamespaces::current);
        }
        String encoded = keys.encode(definition, key, namespaceToken);
        return new Lookup(definition, backend, encoded);
    }

    private long entryEpoch(String encodedKey) {
        AtomicLong epoch = entryEpochs.get(encodedKey);
        return epoch == null ? 0L : epoch.get();
    }

    private void advanceEntryEpoch(String encodedKey) {
        entryEpochs.computeIfAbsent(encodedKey, ignored -> new AtomicLong())
            .incrementAndGet();
    }

    private Object storedValue(NamedCacheDefinition definition, Object value) {
        if (value != null) {
            return value;
        }
        if (!definition.isCacheNullValues()
            && !definition.getProperties().getNegativeCache().isEnabled()) {
            throw new IllegalArgumentException(
                "Null values are disabled for cache " + definition.getName());
        }
        return NULL_VALUE;
    }

    private Duration effectiveTtl(
        NamedCacheDefinition definition, Object value
    ) {
        Duration ttl = value == null
            && definition.getProperties().getNegativeCache().isEnabled()
            ? definition.getProperties().getNegativeCache().getTtl()
            : definition.getTtl();
        if (!definition.getProperties().getTtlJitter().isEnabled()) {
            return ttl;
        }
        int percentage = definition.getProperties().getTtlJitter().getPercentage();
        long ttlNanos;
        try {
            ttlNanos = ttl.toNanos();
        } catch (ArithmeticException overflow) {
            ttlNanos = Long.MAX_VALUE;
        }
        long maximumReduction = Math.max(
            0L, (long) (ttlNanos * (percentage / 100.0D)));
        long reduction = maximumReduction == 0L ? 0L
            : ThreadLocalRandom.current().nextLong(maximumReduction + 1L);
        return Duration.ofNanos(Math.max(1L, ttlNanos - reduction));
    }

    private void enforceMaximumSize(NamedCacheDefinition definition, Object value) {
        int size = json.toBytes(value).length;
        if (size > definition.getMaximumEntrySize()) {
            throw new IllegalArgumentException("Cache value is too large");
        }
    }

    private <T> T convert(Object value, Class<T> type) {
        Objects.requireNonNull(type, "valueType must not be null");
        return type.isInstance(value) ? type.cast(value) : json.convert(value, type);
    }

    private boolean isNullValue(Object value) {
        if (value instanceof CachedNullValue) {
            return true;
        }
        return value instanceof Map<?, ?> map
            && Boolean.TRUE.equals(map.get("cachedNull"));
    }

    private <T> T convert(Object value, CacheType<T> type) {
        JavaType javaType = json.getJsonMapper().getTypeFactory().constructType(type.getType());
        return json.getJsonMapper().convertValue(value, javaType);
    }

    @SuppressWarnings("unchecked")
    private <T> Class<T> typeOf(T value) {
        return (Class<T>) (value == null ? Object.class : value.getClass());
    }

    private <T> T entryValue(BackendCacheEntry entry, Class<T> valueType) {
        return entry == null ? null : convert(entry.getValue(), valueType);
    }

    private <T> OptimisticUpdateResult<T> optimisticResult(
        BackendUpdateResult backend, Class<T> valueType
    ) {
        OptimisticUpdateStatus status = switch (backend.getStatus()) {
            case UPDATED -> OptimisticUpdateStatus.UPDATED;
            case VERSION_CONFLICT -> OptimisticUpdateStatus.VERSION_CONFLICT;
            case NOT_FOUND -> OptimisticUpdateStatus.NOT_FOUND;
        };
        BackendCacheEntry entry = backend.getEntry();
        VersionedValue<T> value = entry == null ? null : new VersionedValue<>(
            entry.getVersion(), convert(entry.getValue(), valueType));
        return OptimisticUpdateResult.<T>builder().status(status).value(value).build();
    }

    private CacheTier tier(NamedCacheDefinition definition) {
        return definition.isMultiLevel() ? CacheTier.L1 : CacheTier.NONE;
    }

    private <T> CacheResult<T> result(
        NamedCacheDefinition definition,
        CacheResultStatus status,
        T value,
        CacheTier tier,
        boolean stale,
        long started,
        CacheFailure failure
    ) {
        CacheResult<T> result = CacheResult.<T>builder()
            .status(status)
            .value(value)
            .cacheName(definition.getName())
            .provider(definition.getProvider())
            .tier(tier)
            .stale(stale)
            .latency(Duration.ofNanos(Math.max(0L, System.nanoTime() - started)))
            .failure(failure)
            .build();
        CacheOperationEvent event = CacheOperationEvent.builder()
            .timestamp(time.now())
            .cacheName(definition.getName())
            .operation("GET")
            .provider(definition.getProvider())
            .outcome(status)
            .tier(tier)
            .fallback(tier == CacheTier.FALLBACK)
            .stale(stale)
            .duration(result.getLatency())
            .errorCategory(failure == null ? null : failure.getCategory())
            .build();
        metrics.record(event);
        events.publish(event);
        return result;
    }

    private boolean failOpen(NamedCacheDefinition definition) {
        return definition.getProperties().getFailurePolicy() == CacheFailurePolicy.FAIL_OPEN;
    }

    private <T> Optional<T> onReadFailure(
        NamedCacheDefinition definition, RuntimeException exception
    ) {
        if (failOpen(definition)) {
            return Optional.empty();
        }
        throw operationFailure("Cache read failed", exception);
    }

    private <T> T readFailure(
        NamedCacheDefinition definition, RuntimeException exception, T failOpenValue
    ) {
        if (failOpen(definition)) {
            return failOpenValue;
        }
        throw operationFailure("Cache read failed", exception);
    }

    private void invokeMutation(NamedCacheDefinition definition, Runnable operation) {
        try {
            operation.run();
        } catch (RuntimeException exception) {
            if (!failOpen(definition)) {
                throw operationFailure("Cache mutation failed", exception);
            }
        }
    }

    private <T> T invokeMutation(
        NamedCacheDefinition definition, Supplier<T> operation, T failOpenValue
    ) {
        try {
            return operation.get();
        } catch (RuntimeException exception) {
            if (failOpen(definition)) {
                return failOpenValue;
            }
            throw operationFailure("Cache mutation failed", exception);
        }
    }

    private PlatformCacheOperationException operationFailure(
        String message, RuntimeException cause
    ) {
        return new PlatformCacheOperationException(OPERATION_ERROR, message, cause);
    }

    private static final class Lookup {
        private final NamedCacheDefinition definition;
        private final CacheBackend backend;
        private final String encodedKey;

        private Lookup(
            NamedCacheDefinition definition, CacheBackend backend, String encodedKey
        ) {
            this.definition = definition;
            this.backend = backend;
            this.encodedKey = encodedKey;
        }

        private NamedCacheDefinition definition() {
            return definition;
        }

        private CacheBackend backend() {
            return backend;
        }

        private String encodedKey() {
            return encodedKey;
        }
    }

    public static final class CachedNullValue {
        public CachedNullValue() {
        }

        public boolean isCachedNull() {
            return true;
        }
    }
}
