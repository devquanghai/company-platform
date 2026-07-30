package com.company.platform.cache.application.resolver;

import com.company.platform.cache.autoconfigure.properties.CacheStoreProperties;
import com.company.platform.cache.autoconfigure.properties.PlatformCacheProperties;
import com.company.platform.cache.domain.exception.PlatformCacheConfigurationException;
import com.company.platform.cache.domain.model.CacheProviderType;
import com.company.platform.cache.support.CacheTestFixtures;
import com.company.platform.cache.support.PlatformCachePropertiesValidator;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CacheDefinitionRegistryTest {

    @Test
    void resolvesCanonicalDefinitionsAndInheritedDefaults() {
        PlatformCacheProperties properties = CacheTestFixtures.validProperties();
        properties.getDefaults().setTtl(Duration.ofMinutes(7));
        properties.getDefaults().setCacheNullValues(true);
        properties.getCaches().get(CacheTestFixtures.CACHE).getKey().setPrefix("custom");

        CacheDefinitionRegistry registry = registry(properties);

        assertThat(registry.requireStore(" LOCAL ").getProvider())
            .isEqualTo(CacheProviderType.CAFFEINE);
        NamedCacheDefinition cache = registry.requireCache(" USERS ");
        assertThat(cache.getTtl()).isEqualTo(Duration.ofMinutes(7));
        assertThat(cache.isCacheNullValues()).isTrue();
        assertThat(cache.getKeyPrefix()).isEqualTo("custom");
        assertThat(cache.isMultiLevel()).isFalse();
        assertThat(registry.getStores()).containsOnlyKeys("local");
        assertThat(registry.getCaches()).containsOnlyKeys("users");
        assertThatThrownBy(() -> registry.getCaches().put("x", cache))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void resolvesMultiLevelDefinition() {
        PlatformCacheProperties properties = CacheTestFixtures.validProperties();
        CacheStoreProperties redis = new CacheStoreProperties();
        redis.setProvider(CacheProviderType.REDIS);
        properties.getStores().put("redis", redis);
        var multi = properties.getCaches().get(CacheTestFixtures.CACHE).getMultiLevel();
        multi.setEnabled(true);
        multi.setL1Store("local");
        multi.setL2Store("redis");

        NamedCacheDefinition cache = registry(properties).requireCache("users");

        assertThat(cache.isMultiLevel()).isTrue();
        assertThat(cache.getProvider()).isEqualTo(CacheProviderType.MULTI_LEVEL);
        assertThat(cache.getPrimaryStore()).isNull();
        assertThat(cache.getL1Store().getName()).isEqualTo("local");
        assertThat(cache.getL2Store().getName()).isEqualTo("redis");
    }

    @Test
    void excludesDisabledEntriesAndSanitizesLookupFailures() {
        PlatformCacheProperties properties = CacheTestFixtures.validProperties();
        properties.getStores().get(CacheTestFixtures.STORE).setEnabled(false);
        properties.getCaches().get(CacheTestFixtures.CACHE).setEnabled(false);
        CacheDefinitionRegistry registry = registry(properties);

        assertThat(registry.getStores()).isEmpty();
        assertThat(registry.getCaches()).isEmpty();
        assertThatThrownBy(() -> registry.requireCache("bad secret!"))
            .isInstanceOf(PlatformCacheConfigurationException.class)
            .hasMessageContaining("bad?secret?");
        assertThatThrownBy(() -> registry.requireStore(null))
            .isInstanceOf(PlatformCacheConfigurationException.class)
            .hasMessageContaining("must not be blank");
    }

    @Test
    void rejectsNonCanonicalAndDuplicateNormalizedNames() {
        PlatformCacheProperties upper = CacheTestFixtures.validProperties();
        upper.setStores(Map.of("Local", upper.getStores().get("local")));
        upper.getCaches().get("users").setStore("Local");
        assertThatThrownBy(() -> registry(upper))
            .isInstanceOf(PlatformCacheConfigurationException.class)
            .hasMessageContaining("canonical");

        PlatformCacheProperties duplicate = CacheTestFixtures.validProperties();
        CacheStoreProperties disabled = new CacheStoreProperties();
        disabled.setEnabled(false);
        duplicate.getStores().put("disabled", disabled);
        assertThat(registry(duplicate).getStores()).doesNotContainKey("disabled");
    }

    @Test
    void validatesConstructorArguments() {
        PlatformCacheProperties properties = CacheTestFixtures.validProperties();
        assertThatNullPointerException()
            .isThrownBy(() -> new CacheDefinitionRegistry(null,
                new PlatformCachePropertiesValidator(properties)));
        assertThatNullPointerException()
            .isThrownBy(() -> new CacheDefinitionRegistry(properties, null));
    }

    private CacheDefinitionRegistry registry(PlatformCacheProperties properties) {
        return new CacheDefinitionRegistry(
            properties, new PlatformCachePropertiesValidator(properties));
    }
}
