package com.company.platform.cache.internal.adapter.redis;

import com.company.platform.cache.autoconfigure.properties.RedisPoolProperties;
import com.company.platform.cache.autoconfigure.properties.RedisProperties;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.springframework.data.redis.connection.RedisClusterConfiguration;
import org.springframework.data.redis.connection.RedisConfiguration;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisSentinelConfiguration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettucePoolingClientConfiguration;
import io.lettuce.core.api.StatefulConnection;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.cluster.ClusterClientOptions;
import io.lettuce.core.cluster.ClusterTopologyRefreshOptions;
import io.lettuce.core.SocketOptions;

import java.util.Objects;

public final class RedisConnectionFactoryBuilder {

    public LettuceConnectionFactory build(RedisProperties properties) {
        Objects.requireNonNull(properties, "properties must not be null");
        RedisConfiguration redis = buildRedisConfiguration(properties);
        LettuceClientConfiguration client = properties.getPool().isEnabled()
            ? buildPooledClient(properties) : buildClient(properties);
        return switch (properties.getMode()) {
            case STANDALONE -> new LettuceConnectionFactory(
                (RedisStandaloneConfiguration) redis, client);
            case SENTINEL -> new LettuceConnectionFactory(
                (RedisSentinelConfiguration) redis, client);
            case CLUSTER -> new LettuceConnectionFactory(
                (RedisClusterConfiguration) redis, client);
        };
    }

    private RedisConfiguration buildRedisConfiguration(RedisProperties properties) {
        return switch (properties.getMode()) {
            case STANDALONE -> standalone(properties);
            case SENTINEL -> sentinel(properties);
            case CLUSTER -> cluster(properties);
        };
    }

    private RedisStandaloneConfiguration standalone(RedisProperties properties) {
        RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration(
            properties.getStandalone().getHost(), properties.getStandalone().getPort());
        configuration.setDatabase(properties.getDatabase());
        applyAuthentication(configuration, properties.getUsername(), properties.getPassword());
        return configuration;
    }

    private RedisSentinelConfiguration sentinel(RedisProperties properties) {
        RedisSentinelConfiguration configuration = new RedisSentinelConfiguration(
            properties.getSentinel().getMaster(),
            new java.util.LinkedHashSet<>(properties.getSentinel().getNodes()));
        configuration.setDatabase(properties.getDatabase());
        applyAuthentication(configuration, properties.getUsername(), properties.getPassword());
        if (hasText(properties.getSentinel().getUsername())) {
            configuration.setSentinelUsername(properties.getSentinel().getUsername());
        }
        if (hasText(properties.getSentinel().getPassword())) {
            configuration.setSentinelPassword(
                RedisPassword.of(properties.getSentinel().getPassword()));
        }
        return configuration;
    }

    private RedisClusterConfiguration cluster(RedisProperties properties) {
        RedisClusterConfiguration configuration =
            new RedisClusterConfiguration(properties.getCluster().getNodes());
        configuration.setMaxRedirects(properties.getCluster().getMaxRedirects());
        if (hasText(properties.getUsername())) {
            configuration.setUsername(properties.getUsername());
        }
        if (hasText(properties.getPassword())) {
            configuration.setPassword(RedisPassword.of(properties.getPassword()));
        }
        return configuration;
    }

    private LettuceClientConfiguration buildClient(RedisProperties properties) {
        LettuceClientConfiguration.LettuceClientConfigurationBuilder builder =
            LettuceClientConfiguration.builder()
                .clientName(properties.getClientName())
                .commandTimeout(properties.getCommandTimeout())
                .shutdownTimeout(properties.getShutdownTimeout());
        applyClientOptions(builder, properties);
        if (properties.getSsl().isEnabled()) {
            LettuceClientConfiguration.LettuceSslClientConfigurationBuilder ssl = builder.useSsl();
            return ssl.build();
        }
        return builder.build();
    }

    private LettuceClientConfiguration buildPooledClient(RedisProperties properties) {
        RedisPoolProperties pool = properties.getPool();
        GenericObjectPoolConfig<StatefulConnection<?, ?>> poolConfig =
            new GenericObjectPoolConfig<>();
        poolConfig.setMaxTotal(pool.getMaxActive());
        poolConfig.setMaxIdle(pool.getMaxIdle());
        poolConfig.setMinIdle(pool.getMinIdle());
        poolConfig.setMaxWait(pool.getMaxWait());
        poolConfig.setTimeBetweenEvictionRuns(pool.getTimeBetweenEvictionRuns());

        LettucePoolingClientConfiguration.LettucePoolingClientConfigurationBuilder builder =
            LettucePoolingClientConfiguration.builder()
                .poolConfig(poolConfig)
                .clientName(properties.getClientName())
                .commandTimeout(properties.getCommandTimeout())
                .shutdownTimeout(properties.getShutdownTimeout());
        applyClientOptions(builder, properties);
        if (properties.getSsl().isEnabled()) {
            LettucePoolingClientConfiguration.LettucePoolingSslClientConfigurationBuilder ssl =
                builder.useSsl();
            return ssl.build();
        }
        return builder.build();
    }

    private void applyAuthentication(
        RedisConfiguration.WithAuthentication configuration,
        String username,
        String password
    ) {
        if (hasText(username)) {
            configuration.setUsername(username);
        }
        if (hasText(password)) {
            configuration.setPassword(RedisPassword.of(password));
        }
    }

    private void applyClientOptions(
        LettuceClientConfiguration.LettuceClientConfigurationBuilder builder,
        RedisProperties properties
    ) {
        ClientOptions options = clientOptions(properties);
        if (options != null) {
            builder.clientOptions(options);
        }
    }

    private ClientOptions clientOptions(RedisProperties properties) {
        SocketOptions socketOptions = SocketOptions.builder()
            .connectTimeout(properties.getConnectTimeout())
            .build();
        if (properties.getMode()
            != com.company.platform.cache.domain.model.RedisDeploymentMode.CLUSTER) {
            return ClientOptions.builder().socketOptions(socketOptions).build();
        }
        ClusterClientOptions.Builder cluster = ClusterClientOptions.builder()
            .socketOptions(socketOptions)
            .validateClusterNodeMembership(true);
        if (!properties.getCluster().getTopologyRefresh().isEnabled()) {
            return cluster.build();
        }
        ClusterTopologyRefreshOptions.Builder topology =
            ClusterTopologyRefreshOptions.builder()
                .enablePeriodicRefresh(
                    properties.getCluster().getTopologyRefresh().getPeriod())
                .dynamicRefreshSources(true);
        if (properties.getCluster().getTopologyRefresh().isAdaptive()) {
            topology.enableAllAdaptiveRefreshTriggers();
        }
        return cluster
            .topologyRefreshOptions(topology.build())
            .build();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
