package com.company.platform.cache.adapter.redis;

import com.company.platform.cache.autoconfigure.properties.RedisProperties;
import com.company.platform.cache.domain.model.RedisDeploymentMode;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class RedisConnectionFactoryBuilderTest {
    private final RedisConnectionFactoryBuilder builder =
        new RedisConnectionFactoryBuilder();

    @Test
    void buildsStandaloneWithAuthenticationAndNonPooledClient() {
        RedisProperties properties = new RedisProperties();
        properties.getPool().setEnabled(false);
        properties.getStandalone().setHost("cache.internal");
        properties.getStandalone().setPort(6380);
        properties.setDatabase(2);
        properties.setUsername("application");
        properties.setPassword("secret");

        LettuceConnectionFactory factory = builder.build(properties);

        assertThat(factory.getStandaloneConfiguration().getHostName())
            .isEqualTo("cache.internal");
        assertThat(factory.getStandaloneConfiguration().getPort()).isEqualTo(6380);
        assertThat(factory.getDatabase()).isEqualTo(2);
        assertThat(factory.getClientConfiguration().getClientName())
            .contains("platform-cache");
    }

    @Test
    void buildsSentinelAndClusterTopologies() {
        RedisProperties sentinel = new RedisProperties();
        sentinel.setMode(RedisDeploymentMode.SENTINEL);
        sentinel.getSentinel().setMaster("cache-master");
        sentinel.getSentinel().setNodes(List.of("one:26379", "two:26379"));
        sentinel.getSentinel().setUsername("sentinel-user");
        sentinel.getSentinel().setPassword("sentinel-secret");
        assertThat(builder.build(sentinel).getSentinelConfiguration().getMaster().getName())
            .isEqualTo("cache-master");

        RedisProperties cluster = new RedisProperties();
        cluster.setMode(RedisDeploymentMode.CLUSTER);
        cluster.getCluster().setNodes(List.of("one:6379", "two:6379"));
        cluster.getCluster().setMaxRedirects(7);
        cluster.setUsername("cluster-user");
        cluster.setPassword("cluster-secret");
        assertThat(builder.build(cluster).getClusterConfiguration().getMaxRedirects())
            .isEqualTo(7);
    }

    @Test
    void buildsSslAndValidatesInput() {
        RedisProperties properties = new RedisProperties();
        properties.getSsl().setEnabled(true);
        properties.getSsl().setVerifyPeer(true);
        assertThat(builder.build(properties).getClientConfiguration().isUseSsl()).isTrue();

        RedisProperties nonPooled = new RedisProperties();
        nonPooled.getPool().setEnabled(false);
        nonPooled.getSsl().setEnabled(true);
        assertThat(builder.build(nonPooled).getClientConfiguration().isUseSsl()).isTrue();
        assertThatNullPointerException().isThrownBy(() -> builder.build(null));
    }

    @Test
    void configuresClusterTopologyRefreshVariants() {
        RedisProperties adaptive = new RedisProperties();
        adaptive.setMode(RedisDeploymentMode.CLUSTER);
        adaptive.getCluster().setNodes(List.of("one:6379"));
        adaptive.getCluster().getTopologyRefresh().setEnabled(true);
        adaptive.getCluster().getTopologyRefresh().setAdaptive(true);
        assertThat(builder.build(adaptive).getClusterConfiguration()).isNotNull();

        RedisProperties disabled = new RedisProperties();
        disabled.setMode(RedisDeploymentMode.CLUSTER);
        disabled.getCluster().setNodes(List.of("one:6379"));
        disabled.getCluster().getTopologyRefresh().setEnabled(false);
        assertThat(builder.build(disabled).getClusterConfiguration()).isNotNull();
    }
}
