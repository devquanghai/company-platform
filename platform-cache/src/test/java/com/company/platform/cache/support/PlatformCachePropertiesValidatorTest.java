package com.company.platform.cache.support;

import com.company.platform.cache.autoconfigure.properties.CacheStoreProperties;
import com.company.platform.cache.autoconfigure.properties.NamedCacheProperties;
import com.company.platform.cache.autoconfigure.properties.PlatformCacheProperties;
import com.company.platform.cache.domain.exception.PlatformCacheConfigurationException;
import com.company.platform.cache.domain.model.CacheProviderType;
import com.company.platform.cache.domain.model.RedisDeploymentMode;
import com.company.platform.cache.domain.policy.CacheFailurePolicy;
import com.company.platform.cache.domain.policy.CacheFallbackMode;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlatformCachePropertiesValidatorTest {

    @Test
    void acceptsDisabledAndValidConfiguration() {
        PlatformCacheProperties disabled = new PlatformCacheProperties();
        disabled.setEnabled(false);
        assertThatCode(() -> new PlatformCachePropertiesValidator(disabled).validate())
            .doesNotThrowAnyException();
        assertThatCode(() -> validator(CacheTestFixtures.validProperties()).validate())
            .doesNotThrowAnyException();
        assertThatNullPointerException()
            .isThrownBy(() -> new PlatformCachePropertiesValidator(null));
    }

    @Test
    void validatesDefaultsAndNames() {
        assertInvalid(properties -> properties.setDefaults(null), "defaults");
        assertInvalid(properties -> properties.getDefaults().setKeyPrefix("bad prefix"), "key-prefix");
        assertInvalid(properties -> properties.getDefaults().setKeyPrefix("{bad}"), "key-prefix");
        assertInvalid(properties -> properties.getDefaults().setKeyPrefix("x".repeat(129)), "key-prefix");
        assertInvalid(properties -> properties.getDefaults().setTtl(Duration.ZERO), "ttl");
        assertInvalid(properties -> properties.getDefaults().setMaximumEntrySize(0), "maximum-entry-size");
        assertInvalid(properties -> {
            CacheStoreProperties store = properties.getStores().remove(CacheTestFixtures.STORE);
            properties.getStores().put("BAD NAME", store);
        }, "invalid name");
        assertInvalid(properties -> properties.getStores().put("broken", null), "must not be null");
        assertInvalid(properties -> properties.getStores().put(null,
            new CacheStoreProperties()), "invalid name");
    }

    @Test
    void validatesCaffeineAndStoreProvider() {
        assertInvalid(properties -> properties.getStores().get(CacheTestFixtures.STORE)
            .setProvider(null), "provider");
        assertInvalid(properties -> properties.getStores().get(CacheTestFixtures.STORE)
            .setProvider(CacheProviderType.MULTI_LEVEL), "cannot be MULTI_LEVEL");
        assertInvalid(properties -> properties.getStores().get(CacheTestFixtures.STORE)
            .setCaffeine(null), "caffeine");
        assertInvalid(properties -> properties.getStores().get(CacheTestFixtures.STORE)
            .getCaffeine().setMaximumSize(0), "maximum-size");
        assertInvalid(properties -> properties.getStores().get(CacheTestFixtures.STORE)
            .getCaffeine().setExpireAfterWrite(Duration.ZERO), "expire-after-write");
        assertInvalid(properties -> {
            var caffeine = properties.getStores().get(CacheTestFixtures.STORE).getCaffeine();
            caffeine.setWeakValues(true);
            caffeine.setSoftValues(true);
        }, "mutually exclusive");
    }

    @Test
    void validatesRedisModesSerializationAndTimeouts() {
        assertInvalid(properties -> redis(properties).setSsl(null), "null configuration section");
        assertInvalid(properties -> redis(properties).setMode(null), ".mode");
        assertInvalid(properties -> redis(properties).setCommandTimeout(Duration.ZERO), "command-timeout");
        assertInvalid(properties -> redis(properties).setConnectTimeout(Duration.ZERO), "connect-timeout");
        assertInvalid(properties -> redis(properties).setShutdownTimeout(Duration.ZERO), "shutdown-timeout");
        assertInvalid(properties -> redis(properties).getPool().setMaxIdle(100), "pool requires");
        assertInvalid(properties -> redis(properties).getPool().setMaxWait(Duration.ZERO), "pool.max-wait");
        assertInvalid(properties -> {
            redis(properties).getSsl().setEnabled(true);
            redis(properties).getSsl().setVerifyPeer(false);
        }, "verify-peer");
        assertInvalid(properties -> redis(properties).getSerialization()
            .setValueEnvelopeEnabled(false), "JSON value envelope");
        assertInvalid(properties -> redis(properties).getSerialization()
            .setSchemaId("bad schema"), "schema-id");
        assertInvalid(properties -> redis(properties).getSerialization()
            .setSchemaVersion(0), "schema-version");
        assertInvalid(properties -> redis(properties).getStandalone().setHost(" "), "standalone.host");
        assertInvalid(properties -> redis(properties).getStandalone().setPort(0), "standalone.port");
        assertInvalid(properties -> redis(properties).getStandalone().setPort(70_000), "standalone.port");
        assertInvalid(properties -> {
            redis(properties).setMode(RedisDeploymentMode.SENTINEL);
            redis(properties).getSentinel().setMaster("master");
        }, "at least one node");
        assertInvalid(properties -> {
            redis(properties).setMode(RedisDeploymentMode.SENTINEL);
            redis(properties).getSentinel().setMaster("master");
            redis(properties).getSentinel().setNodes(List.of("bad-node"));
        }, "invalid host:port");
        assertInvalid(properties -> {
            redis(properties).setMode(RedisDeploymentMode.CLUSTER);
            redis(properties).getCluster().setNodes(List.of("localhost:6379"));
            redis(properties).setDatabase(1);
        }, "database must be 0");
        assertInvalid(properties -> {
            redis(properties).setMode(RedisDeploymentMode.CLUSTER);
            redis(properties).getCluster().setNodes(List.of("localhost:6379"));
            redis(properties).getCluster().setTopologyRefresh(null);
        }, "topology-refresh");
        assertInvalid(properties -> {
            redis(properties).setMode(RedisDeploymentMode.CLUSTER);
            redis(properties).getCluster().setNodes(List.of("localhost:6379"));
            redis(properties).getCluster().getTopologyRefresh().setEnabled(true);
            redis(properties).getCluster().getTopologyRefresh().setPeriod(Duration.ZERO);
        }, "topology-refresh.period");

        PlatformCacheProperties validCluster = redisProperties();
        redis(validCluster).setMode(RedisDeploymentMode.CLUSTER);
        redis(validCluster).getCluster().setNodes(List.of("localhost:6379"));
        assertThatCode(() -> validator(validCluster).validate()).doesNotThrowAnyException();

        PlatformCacheProperties externalFactory = redisProperties();
        externalFactory.getStores().get(CacheTestFixtures.STORE)
            .setConnectionFactoryBean("applicationRedisConnectionFactory");
        externalFactory.getStores().get(CacheTestFixtures.STORE).setRedis(null);
        assertThatCode(() -> validator(externalFactory).validate()).doesNotThrowAnyException();
    }

    @Test
    void validatesNamedCachePolicies() {
        assertInvalid(properties -> properties.getCaches().put("broken", null), "must not be null");
        assertInvalid(properties -> cache(properties).setKey(null), "null policy section");
        assertInvalid(properties -> cache(properties).setFailurePolicy(null), "failure-policy");
        assertInvalid(properties -> cache(properties).setTtl(Duration.ZERO), ".ttl");
        assertInvalid(properties -> cache(properties).getKey().setVersion("bad version"), "invalid name");
        assertInvalid(properties -> cache(properties).setStore("missing"), "unknown store");
        assertInvalid(properties -> properties.getStores().get(CacheTestFixtures.STORE)
            .setEnabled(false), "disabled store");
        assertInvalid(properties -> {
            properties.getStores().get(CacheTestFixtures.STORE).setProvider(CacheProviderType.NOOP);
            cache(properties).setFailurePolicy(CacheFailurePolicy.FAIL_CLOSED);
        }, "NOOP");
        assertInvalid(properties -> {
            cache(properties).setCoordination(true);
            cache(properties).setFailurePolicy(CacheFailurePolicy.FAIL_OPEN);
        }, "coordination cache");
        assertInvalid(properties -> cache(properties).getStampede()
            .setMaximumInflight(0), "maximum-inflight");
        assertInvalid(properties -> cache(properties).getStampede()
            .setWaitTimeout(Duration.ZERO), "wait-timeout");
        assertInvalid(properties -> cache(properties).getTtlJitter()
            .setPercentage(51), "between 0 and 50");
        assertInvalid(properties -> cache(properties).getTtlJitter()
            .setPercentage(-1), "between 0 and 50");
        assertInvalid(properties -> {
            cache(properties).getNegativeCache().setEnabled(true);
            cache(properties).getNegativeCache().setTtl(Duration.ofHours(1));
        }, "shorter than ttl");
        assertInvalid(properties -> {
            cache(properties).setFailurePolicy(CacheFailurePolicy.FALLBACK_LOCAL);
        }, "requires fallback.enabled");
    }

    @Test
    void validatesMultiLevelAndFallback() {
        assertInvalid(properties -> {
            cache(properties).getMultiLevel().setEnabled(true);
            cache(properties).getMultiLevel().setL1Store(CacheTestFixtures.STORE);
            cache(properties).getMultiLevel().setL2Store(CacheTestFixtures.STORE);
        }, "l2-store must reference REDIS");

        assertInvalid(properties -> {
            addRedis(properties);
            cache(properties).getMultiLevel().setEnabled(true);
            cache(properties).getMultiLevel().setL1Store("redis");
            cache(properties).getMultiLevel().setL2Store(CacheTestFixtures.STORE);
        }, "l1-store must reference CAFFEINE");
        assertInvalid(properties -> {
            addRedis(properties);
            cache(properties).getMultiLevel().setEnabled(true);
            cache(properties).getMultiLevel().setL1Store(CacheTestFixtures.STORE);
            cache(properties).getMultiLevel().setL2Store("redis");
            cache(properties).getMultiLevel().setL1Ttl(Duration.ZERO);
        }, "l1-ttl");
        assertInvalid(properties -> {
            addRedis(properties);
            cache(properties).getMultiLevel().setEnabled(true);
            cache(properties).getMultiLevel().setL1Store(CacheTestFixtures.STORE);
            cache(properties).getMultiLevel().setL2Store("redis");
            cache(properties).getMultiLevel().setL2Ttl(Duration.ZERO);
        }, "l2-ttl");

        assertInvalid(properties -> {
            addRedis(properties);
            cache(properties).getMultiLevel().setEnabled(true);
            cache(properties).getMultiLevel().setL1Store(CacheTestFixtures.STORE);
            cache(properties).getMultiLevel().setL2Store("redis");
            cache(properties).getMultiLevel().setL1Ttl(Duration.ofHours(2));
        }, "must not exceed");

        assertInvalid(properties -> {
            cache(properties).getFallback().setEnabled(true);
            cache(properties).getFallback().setLocalStore(CacheTestFixtures.STORE);
            cache(properties).getFallback().setMode(CacheFallbackMode.LOCAL_READ_WRITE);
        }, "allow-local-write-fallback");
        assertInvalid(properties -> {
            cache(properties).getFallback().setEnabled(true);
            cache(properties).getFallback().setMode(null);
        }, "enabled fallback mode");
        assertInvalid(properties -> {
            addRedis(properties);
            cache(properties).getFallback().setEnabled(true);
            cache(properties).getFallback().setMode(CacheFallbackMode.READ_THROUGH);
            cache(properties).getFallback().setLocalStore("redis");
        }, "must reference CAFFEINE");
        assertInvalid(properties -> {
            cache(properties).getFallback().setEnabled(true);
            cache(properties).getFallback().setLocalStore(CacheTestFixtures.STORE);
            cache(properties).getFallback().setMode(CacheFallbackMode.READ_THROUGH);
            cache(properties).getFallback().setTtl(Duration.ZERO);
        }, "fallback.ttl");
        assertInvalid(properties -> {
            cache(properties).getFallback().setEnabled(true);
            cache(properties).getFallback().setLocalStore(CacheTestFixtures.STORE);
            cache(properties).getFallback().setMode(CacheFallbackMode.READ_THROUGH);
            cache(properties).getFallback().setMaximumStale(Duration.ZERO);
        }, "maximum-stale");
        assertInvalid(properties -> {
            cache(properties).setCoordination(true);
            cache(properties).setFailurePolicy(CacheFailurePolicy.FAIL_CLOSED);
            cache(properties).getFallback().setEnabled(true);
            cache(properties).getFallback().setMode(CacheFallbackMode.READ_THROUGH);
            cache(properties).getFallback().setLocalStore(CacheTestFixtures.STORE);
        }, "forbidden");
    }

    private void assertInvalid(
        java.util.function.Consumer<PlatformCacheProperties> mutation,
        String message
    ) {
        PlatformCacheProperties properties = CacheTestFixtures.validProperties();
        mutation.accept(properties);
        assertThatThrownBy(() -> validator(properties).validate())
            .isInstanceOf(PlatformCacheConfigurationException.class)
            .hasMessageContaining(message);
    }

    private PlatformCacheProperties redisProperties() {
        PlatformCacheProperties properties = CacheTestFixtures.validProperties();
        properties.getStores().get(CacheTestFixtures.STORE).setProvider(CacheProviderType.REDIS);
        return properties;
    }

    private void addRedis(PlatformCacheProperties properties) {
        CacheStoreProperties redis = new CacheStoreProperties();
        redis.setProvider(CacheProviderType.REDIS);
        properties.getStores().put("redis", redis);
    }

    private com.company.platform.cache.autoconfigure.properties.RedisProperties redis(
        PlatformCacheProperties properties
    ) {
        properties.getStores().get(CacheTestFixtures.STORE).setProvider(CacheProviderType.REDIS);
        return properties.getStores().get(CacheTestFixtures.STORE).getRedis();
    }

    private NamedCacheProperties cache(PlatformCacheProperties properties) {
        return properties.getCaches().get(CacheTestFixtures.CACHE);
    }

    private PlatformCachePropertiesValidator validator(PlatformCacheProperties properties) {
        return new PlatformCachePropertiesValidator(properties);
    }
}
