package com.company.platform.cache.support;

import com.company.platform.cache.autoconfigure.properties.CacheStoreProperties;
import com.company.platform.cache.autoconfigure.properties.FallbackProperties;
import com.company.platform.cache.autoconfigure.properties.MultiLevelProperties;
import com.company.platform.cache.autoconfigure.properties.NamedCacheProperties;
import com.company.platform.cache.autoconfigure.properties.PlatformCacheProperties;
import com.company.platform.cache.autoconfigure.properties.RedisProperties;
import com.company.platform.cache.domain.exception.PlatformCacheConfigurationException;
import com.company.platform.cache.domain.model.CacheProviderType;
import com.company.platform.cache.domain.model.RedisDeploymentMode;
import com.company.platform.cache.domain.policy.CacheFailurePolicy;
import com.company.platform.cache.domain.policy.CacheFallbackMode;

import java.time.Duration;
import java.util.Objects;
import java.util.regex.Pattern;

public final class PlatformCachePropertiesValidator {
    private static final Pattern SAFE_NAME =
        Pattern.compile("[a-zA-Z0-9][a-zA-Z0-9._-]{0,126}");
    private static final Pattern NODE =
        Pattern.compile("[a-zA-Z0-9._-]+:[1-9][0-9]{0,4}");

    private final PlatformCacheProperties properties;

    public PlatformCachePropertiesValidator(PlatformCacheProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    public void validate() {
        if (!properties.isEnabled()) {
            return;
        }
        if (properties.getDefaults() == null) {
            fail("platform.cache.defaults must be configured");
        }
        requireText("platform.cache.defaults.key-prefix",
            properties.getDefaults().getKeyPrefix());
        requireSafePrefix("platform.cache.defaults.key-prefix",
            properties.getDefaults().getKeyPrefix());
        requirePositive("platform.cache.defaults.ttl", properties.getDefaults().getTtl());
        if (properties.getDefaults().getMaximumEntrySize() < 1) {
            fail("platform.cache.defaults.maximum-entry-size must be at least 1");
        }
        properties.getStores().forEach(this::validateStore);
        properties.getCaches().forEach(this::validateCache);
    }

    private void validateStore(String name, CacheStoreProperties store) {
        requireName("platform.cache.stores", name);
        if (store == null) {
            fail("platform.cache.stores." + name + " must not be null");
        }
        if (!store.isEnabled()) {
            return;
        }
        if (store.getProvider() == null) {
            fail("platform.cache.stores." + name + ".provider must not be null");
        }
        if (store.getProvider() == CacheProviderType.MULTI_LEVEL) {
            fail("platform.cache.stores." + name
                + ".provider cannot be MULTI_LEVEL");
        }
        if (store.getProvider() == CacheProviderType.CAFFEINE) {
            if (store.getCaffeine() == null) {
                fail("platform.cache.stores." + name + ".caffeine must be configured");
            }
            if (store.getCaffeine().getMaximumSize() < 1) {
                fail("platform.cache.stores." + name
                    + ".caffeine.maximum-size must be at least 1");
            }
            requirePositive("platform.cache.stores." + name
                + ".caffeine.expire-after-write",
                store.getCaffeine().getExpireAfterWrite());
            int valueStrengths = (store.getCaffeine().isWeakValues() ? 1 : 0)
                + (store.getCaffeine().isSoftValues() ? 1 : 0);
            if (valueStrengths > 1) {
                fail("platform.cache.stores." + name
                    + ".caffeine weak-values and soft-values are mutually exclusive");
            }
        }
        if (store.getProvider() == CacheProviderType.REDIS
            && isBlank(store.getConnectionFactoryBean())) {
            if (store.getRedis() == null) {
                fail("platform.cache.stores." + name + ".redis must be configured");
            }
            validateRedis(name, store.getRedis());
        }
    }

    private void validateRedis(String name, RedisProperties redis) {
        String path = "platform.cache.stores." + name + ".redis";
        if (redis.getSsl() == null || redis.getPool() == null
            || redis.getStandalone() == null || redis.getSentinel() == null
            || redis.getCluster() == null || redis.getSerialization() == null
            || redis.getResilience() == null) {
            fail(path + " contains a null configuration section");
        }
        if (redis.getMode() == null) {
            fail(path + ".mode must not be null");
        }
        requirePositive(path + ".command-timeout", redis.getCommandTimeout());
        requirePositive(path + ".connect-timeout", redis.getConnectTimeout());
        requirePositive(path + ".shutdown-timeout", redis.getShutdownTimeout());
        if (redis.getSsl().isEnabled() && !redis.getSsl().isVerifyPeer()) {
            fail(path + ".ssl.verify-peer cannot be disabled");
        }
        if (redis.getPool().isEnabled()) {
            if (redis.getPool().getMaxActive() < 1
                || redis.getPool().getMaxIdle() < redis.getPool().getMinIdle()
                || redis.getPool().getMaxIdle() > redis.getPool().getMaxActive()) {
                fail(path + ".pool requires max-active >= max-idle >= min-idle");
            }
            requirePositive(path + ".pool.max-wait", redis.getPool().getMaxWait());
        }
        if (!redis.getSerialization().isValueEnvelopeEnabled()
            || !"JSON".equalsIgnoreCase(redis.getSerialization().getValue())) {
            fail(path + ".serialization must use the JSON value envelope");
        }
        requireName(path + ".serialization.schema-id",
            redis.getSerialization().getSchemaId());
        if (redis.getSerialization().getSchemaVersion() < 1) {
            fail(path + ".serialization.schema-version must be at least 1");
        }
        if (redis.getMode() == RedisDeploymentMode.STANDALONE) {
            requireText(path + ".standalone.host",
                redis.getStandalone().getHost());
            requirePort(path + ".standalone.port",
                redis.getStandalone().getPort());
        } else if (redis.getMode() == RedisDeploymentMode.SENTINEL) {
            requireText(path + ".sentinel.master",
                redis.getSentinel().getMaster());
            requireNodes(path + ".sentinel.nodes",
                redis.getSentinel().getNodes());
        } else {
            requireNodes(path + ".cluster.nodes",
                redis.getCluster().getNodes());
            if (redis.getDatabase() != 0) {
                fail(path + ".database must be 0 in CLUSTER mode");
            }
            if (redis.getCluster().getTopologyRefresh() == null) {
                fail(path + ".cluster.topology-refresh must be configured");
            }
            if (redis.getCluster().getTopologyRefresh().isEnabled()) {
                requirePositive(path + ".cluster.topology-refresh.period",
                    redis.getCluster().getTopologyRefresh().getPeriod());
            }
        }
    }

    private void validateCache(String name, NamedCacheProperties cache) {
        requireName("platform.cache.caches", name);
        if (cache == null) {
            fail("platform.cache.caches." + name + " must not be null");
        }
        if (!cache.isEnabled()) {
            return;
        }
        if (cache.getKey() == null || cache.getFallback() == null
            || cache.getMultiLevel() == null || cache.getStampede() == null
            || cache.getTtlJitter() == null || cache.getNegativeCache() == null) {
            fail("platform.cache.caches." + name + " contains a null policy section");
        }
        if (cache.getFailurePolicy() == null) {
            fail("platform.cache.caches." + name + ".failure-policy must not be null");
        }
        if (!isBlank(cache.getKey().getPrefix())) {
            requireSafePrefix(
                "platform.cache.caches." + name + ".key.prefix",
                cache.getKey().getPrefix());
        }
        requireName(
            "platform.cache.caches." + name + ".key.version",
            cache.getKey().getVersion());
        Duration ttl = cache.getTtl() == null
            ? properties.getDefaults().getTtl() : cache.getTtl();
        requirePositive("platform.cache.caches." + name + ".ttl", ttl);
        if (cache.getMultiLevel().isEnabled()) {
            validateMultiLevel(name, cache.getMultiLevel());
        } else {
            CacheStoreProperties store = enabledStore(name, cache.getStore());
            if (store.getProvider() == CacheProviderType.NOOP
                && cache.getFailurePolicy() == CacheFailurePolicy.FAIL_CLOSED) {
                fail("platform.cache.caches." + name
                    + " cannot combine NOOP with FAIL_CLOSED");
            }
        }
        validateFallback(name, cache, cache.getFallback());
        if (cache.getFailurePolicy() == CacheFailurePolicy.FALLBACK_LOCAL
            && !cache.getFallback().isEnabled()) {
            fail("platform.cache.caches." + name
                + " requires fallback.enabled for FALLBACK_LOCAL");
        }
        if (cache.getNegativeCache().isEnabled()) {
            requirePositive("platform.cache.caches." + name
                + ".negative-cache.ttl", cache.getNegativeCache().getTtl());
            if (cache.getNegativeCache().getTtl().compareTo(ttl) >= 0) {
                fail("platform.cache.caches." + name
                    + ".negative-cache.ttl must be shorter than ttl");
            }
        }
        int jitter = cache.getTtlJitter().getPercentage();
        if (jitter < 0 || jitter > 50) {
            fail("platform.cache.caches." + name
                + ".ttl-jitter.percentage must be between 0 and 50");
        }
        if (cache.isCoordination()
            && cache.getFailurePolicy() != CacheFailurePolicy.FAIL_CLOSED) {
            fail("platform.cache.caches." + name
                + ".failure-policy must be FAIL_CLOSED for coordination cache");
        }
        requirePositive("platform.cache.caches." + name
            + ".stampede.wait-timeout", cache.getStampede().getWaitTimeout());
        if (cache.getStampede().getMaximumInflight() < 1) {
            fail("platform.cache.caches." + name
                + ".stampede.maximum-inflight must be at least 1");
        }
    }

    private void validateMultiLevel(String cacheName, MultiLevelProperties multi) {
        CacheStoreProperties l1 = enabledStore(cacheName, multi.getL1Store());
        CacheStoreProperties l2 = enabledStore(cacheName, multi.getL2Store());
        if (l1.getProvider() != CacheProviderType.CAFFEINE) {
            fail("platform.cache.caches." + cacheName
                + ".multi-level.l1-store must reference CAFFEINE");
        }
        if (l2.getProvider() != CacheProviderType.REDIS) {
            fail("platform.cache.caches." + cacheName
                + ".multi-level.l2-store must reference REDIS");
        }
        requirePositive("platform.cache.caches." + cacheName
            + ".multi-level.l1-ttl", multi.getL1Ttl());
        requirePositive("platform.cache.caches." + cacheName
            + ".multi-level.l2-ttl", multi.getL2Ttl());
        if (multi.getL1Ttl().compareTo(multi.getL2Ttl()) > 0) {
            fail("platform.cache.caches." + cacheName
                + ".multi-level.l1-ttl must not exceed l2-ttl");
        }
    }

    private void validateFallback(
        String name, NamedCacheProperties cache, FallbackProperties fallback
    ) {
        if (!fallback.isEnabled()) {
            return;
        }
        if (fallback.getMode() == null || fallback.getMode() == CacheFallbackMode.NONE) {
            fail("platform.cache.caches." + name
                + ".fallback.mode must select an enabled fallback mode");
        }
        if (cache.isCoordination()) {
            fail("platform.cache.caches." + name
                + ".fallback is forbidden for coordination cache");
        }
        CacheStoreProperties local = enabledStore(name, fallback.getLocalStore());
        if (local.getProvider() != CacheProviderType.CAFFEINE) {
            fail("platform.cache.caches." + name
                + ".fallback.local-store must reference CAFFEINE");
        }
        if (fallback.getMode() == CacheFallbackMode.LOCAL_READ_WRITE
            && !fallback.isAllowLocalWriteFallback()) {
            fail("platform.cache.caches." + name
                + ".fallback.allow-local-write-fallback must be true");
        }
        requirePositive("platform.cache.caches." + name
            + ".fallback.ttl", fallback.getTtl());
        requirePositive("platform.cache.caches." + name
            + ".fallback.maximum-stale", fallback.getMaximumStale());
    }

    private CacheStoreProperties enabledStore(String cache, String storeName) {
        requireText("platform.cache.caches." + cache + ".store", storeName);
        CacheStoreProperties store = properties.getStores().get(storeName);
        if (store == null) {
            fail("platform.cache.caches." + cache
                + " references unknown store " + safe(storeName));
        }
        if (!store.isEnabled()) {
            fail("platform.cache.caches." + cache
                + " references disabled store " + safe(storeName));
        }
        return store;
    }

    private void requireNodes(String path, Iterable<String> nodes) {
        boolean found = false;
        for (String node : nodes) {
            found = true;
            if (node == null || !NODE.matcher(node).matches()) {
                fail(path + " contains an invalid host:port");
            }
        }
        if (!found) {
            fail(path + " must contain at least one node");
        }
    }

    private void requireName(String path, String name) {
        if (name == null || !SAFE_NAME.matcher(name).matches()) {
            fail(path + " contains an invalid name");
        }
    }

    private void requireText(String path, String value) {
        if (isBlank(value)) {
            fail(path + " must not be blank");
        }
    }

    private void requireSafePrefix(String path, String value) {
        if (value.length() > 128 || value.indexOf('{') >= 0 || value.indexOf('}') >= 0
            || value.chars().anyMatch(Character::isWhitespace)) {
            fail(path + " must be at most 128 characters without braces or whitespace");
        }
    }

    private void requirePositive(String path, Duration value) {
        if (value == null || value.isZero() || value.isNegative()) {
            fail(path + " must be greater than zero");
        }
    }

    private void requirePort(String path, int value) {
        if (value < 1 || value > 65_535) {
            fail(path + " must be between 1 and 65535");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String safe(String value) {
        if (value == null) {
            return "<null>";
        }
        return value.length() > 64 ? value.substring(0, 64) : value;
    }

    private void fail(String message) {
        throw new PlatformCacheConfigurationException(message);
    }
}
