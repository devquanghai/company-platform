package com.company.platform.cache.autoconfigure.properties;

import com.company.platform.cache.domain.model.RedisDeploymentMode;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.time.Duration;

@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RedisProperties {
    RedisDeploymentMode mode = RedisDeploymentMode.STANDALONE;
    int database;
    String clientName = "platform-cache";
    Duration commandTimeout = Duration.ofSeconds(2);
    Duration connectTimeout = Duration.ofSeconds(2);
    Duration shutdownTimeout = Duration.ofSeconds(2);
    String username;
    String password;
    @Valid RedisSslProperties ssl = new RedisSslProperties();
    @Valid RedisPoolProperties pool = new RedisPoolProperties();
    @Valid RedisStandaloneProperties standalone = new RedisStandaloneProperties();
    @Valid RedisSentinelProperties sentinel = new RedisSentinelProperties();
    @Valid RedisClusterProperties cluster = new RedisClusterProperties();
    @Valid SerializationProperties serialization = new SerializationProperties();
    @Valid ResilienceProperties resilience = new ResilienceProperties();
}
