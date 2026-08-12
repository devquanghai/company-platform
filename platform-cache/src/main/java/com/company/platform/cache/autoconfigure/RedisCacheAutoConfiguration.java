package com.company.platform.cache.autoconfigure;

import com.company.platform.cache.internal.adapter.redis.RedisCacheBackend;
import com.company.platform.cache.internal.application.port.out.CacheBackend;
import com.company.platform.cache.internal.application.port.out.RedisCacheBackendFactory;
import com.company.platform.cache.internal.application.resolver.CacheDefinitionRegistry;
import com.company.platform.cache.internal.application.resolver.CacheStoreDefinition;
import com.company.platform.cache.autoconfigure.properties.PlatformCacheProperties;
import com.company.platform.cache.domain.model.CacheProviderType;
import com.company.platform.core.json.JsonMapperHelper;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@AutoConfiguration(
    afterName = "org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration")
@ConditionalOnClass(RedisConnectionFactory.class)
@ConditionalOnProperty(
    prefix = "platform.cache", name = "enabled", matchIfMissing = true)
public class RedisCacheAutoConfiguration {

    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean(
        RedisCacheBackendFactory.class)
    RedisCacheBackendFactory platformRedisBackendFactory(
        PlatformCacheProperties properties,
        CacheDefinitionRegistry definitions,
        ListableBeanFactory beans,
        ObjectProvider<RedisConnectionFactory> connectionFactories,
        JsonMapperHelper json
    ) {
        Map<String, RedisTemplate<String, byte[]>> templates = new LinkedHashMap<>();
        definitions.getStores().values().stream()
            .filter(store -> store.getProvider() == CacheProviderType.REDIS)
            .forEach(store -> templates.put(
                store.getName(), template(connectionFactory(
                    store, beans, connectionFactories))));
        return new DefaultRedisCacheBackendFactory(
            properties, Collections.unmodifiableMap(templates), json);
    }

    private RedisConnectionFactory connectionFactory(
        CacheStoreDefinition store,
        ListableBeanFactory beans,
        ObjectProvider<RedisConnectionFactory> connectionFactories
    ) {
        String configured = store.getProperties().getConnectionFactoryBean();
        if (configured != null && !configured.isBlank()) {
            return beans.getBean(configured, RedisConnectionFactory.class);
        }
        RedisConnectionFactory candidate = connectionFactories.getIfUnique();
        if (candidate != null) {
            return candidate;
        }
        if (beans.containsBean("redisConnectionFactory")) {
            return beans.getBean("redisConnectionFactory", RedisConnectionFactory.class);
        }
        throw new IllegalStateException(
            "Redis cache requires one Boot-managed RedisConnectionFactory or connection-factory-bean");
    }

    private RedisTemplate<String, byte[]> template(RedisConnectionFactory factory) {
        RedisTemplate<String, byte[]> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(RedisSerializer.byteArray());
        template.setValueSerializer(RedisSerializer.byteArray());
        template.setHashValueSerializer(RedisSerializer.byteArray());
        template.afterPropertiesSet();
        return template;
    }

    private static final class DefaultRedisCacheBackendFactory
        implements RedisCacheBackendFactory {
        private final PlatformCacheProperties properties;
        private final Map<String, RedisTemplate<String, byte[]>> templates;
        private final JsonMapperHelper json;

        DefaultRedisCacheBackendFactory(
            PlatformCacheProperties properties,
            Map<String, RedisTemplate<String, byte[]>> templates,
            JsonMapperHelper json
        ) {
            this.properties = properties;
            this.templates = templates;
            this.json = json;
        }

        @Override
        public CacheBackend create(String storeName, String cacheName) {
            RedisTemplate<String, byte[]> template = templates.get(storeName);
            if (template == null) {
                throw new IllegalStateException("No Redis template for store " + storeName);
            }
            var serialization = properties.getStores().get(storeName)
                .getRedis().getSerialization();
            return new RedisCacheBackend(
                template, json, properties.getDefaults().getKeyPrefix(), cacheName,
                serialization.getSchemaId(), serialization.getSchemaVersion());
        }
    }
}
