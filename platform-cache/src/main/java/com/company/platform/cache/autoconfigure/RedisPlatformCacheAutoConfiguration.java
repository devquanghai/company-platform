package com.company.platform.cache.autoconfigure;

import com.company.platform.core.json.JsonMapperHelper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.cache.autoconfigure.CacheProperties;
import org.springframework.boot.cache.autoconfigure.RedisCacheManagerBuilderCustomizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.cache.BatchStrategies;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;

@AutoConfiguration(beforeName =
    "org.springframework.boot.cache.autoconfigure.RedisCacheConfiguration")
@ConditionalOnClass(RedisCacheConfiguration.class)
@ConditionalOnProperty(
    prefix = "platform.cache", name = "provider", havingValue = "redis")
public class RedisPlatformCacheAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(RedisCacheConfiguration.class)
    RedisCacheConfiguration platformRedisCacheConfiguration(
        JsonMapperHelper json,
        CacheProperties cacheProperties
    ) {
        var typeValidator = BasicPolymorphicTypeValidator.builder()
            .allowIfSubType("com.company.")
            .allowIfSubType("java.lang.")
            .allowIfSubType("java.time.")
            .allowIfSubType("java.util.")
            .allowIfSubTypeIsArray()
            .build();
        var serializer = GenericJacksonJsonRedisSerializer
            .builder(json.getJsonMapper()::rebuild)
            .enableDefaultTyping(typeValidator)
            .enableSpringCacheNullValueSupport()
            .build();
        RedisCacheConfiguration configuration = RedisCacheConfiguration.defaultCacheConfig()
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair.fromSerializer(serializer));
        CacheProperties.Redis redis = cacheProperties.getRedis();
        if (redis.getTimeToLive() != null) {
            configuration = configuration.entryTtl(redis.getTimeToLive());
        }
        if (redis.getKeyPrefix() != null) {
            configuration = configuration.prefixCacheNameWith(redis.getKeyPrefix());
        }
        if (!redis.isCacheNullValues()) {
            configuration = configuration.disableCachingNullValues();
        }
        if (!redis.isUseKeyPrefix()) {
            configuration = configuration.disableKeyPrefix();
        }
        return configuration;
    }

    @Bean
    @ConditionalOnBean(RedisConnectionFactory.class)
    @ConditionalOnMissingBean(RedisCacheWriter.class)
    RedisCacheWriter platformRedisCacheWriter(RedisConnectionFactory connectionFactory) {
        return RedisCacheWriter.nonLockingRedisCacheWriter(
            connectionFactory, BatchStrategies.scan(1_000));
    }

    @Bean
    @ConditionalOnBean(RedisCacheWriter.class)
    RedisCacheManagerBuilderCustomizer platformRedisCacheWriterCustomizer(
        RedisCacheWriter cacheWriter
    ) {
        return builder -> builder.cacheWriter(cacheWriter);
    }
}
