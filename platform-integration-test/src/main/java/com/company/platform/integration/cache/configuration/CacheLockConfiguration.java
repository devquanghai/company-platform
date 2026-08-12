package com.company.platform.integration.cache.configuration;

import com.company.platform.cache.api.lock.DistributedLockOperations;
import com.company.platform.cache.api.lock.LockHandle;
import com.company.platform.cache.api.lock.LockOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Configuration(proxyBeanMethods = false)
public class CacheLockConfiguration {

    @Bean
    @ConditionalOnMissingBean(DistributedLockOperations.class)
    DistributedLockOperations integrationDistributedLockOperations(
        RedisConnectionFactory connectionFactory
    ) {
        return new RedisDistributedLockOperations(connectionFactory);
    }

    private static final class RedisDistributedLockOperations
        implements DistributedLockOperations {
        private static final DefaultRedisScript<Long> UNLOCK_SCRIPT =
            new DefaultRedisScript<>("""
                if redis.call('get', KEYS[1]) == ARGV[1] then
                  return redis.call('del', KEYS[1])
                end
                return 0
                """, Long.class);

        private final StringRedisTemplate redis;

        private RedisDistributedLockOperations(
            RedisConnectionFactory connectionFactory
        ) {
            redis = new StringRedisTemplate(connectionFactory);
            redis.afterPropertiesSet();
        }

        @Override
        public <T> T executeWithLock(
            String lockName, LockOptions options, Supplier<T> action
        ) {
            Objects.requireNonNull(action, "action");
            LockHandle handle = tryLock(lockName, options)
                .orElseThrow(() -> new IllegalStateException(
                    "Distributed lock acquisition timed out"));
            try (handle) {
                T result = action.get();
                if (!handle.isOwned()) {
                    throw new IllegalStateException(
                        "Distributed lock ownership was lost during execution");
                }
                return result;
            }
        }

        @Override
        public Optional<LockHandle> tryLock(
            String lockName, LockOptions options
        ) {
            requireArguments(lockName, options);
            String redisKey = "platform-integration:lock:" + lockName;
            String ownerToken = UUID.randomUUID().toString();
            long deadline = System.nanoTime() + options.getWaitTime().toNanos();
            do {
                Boolean acquired = redis.opsForValue().setIfAbsent(
                    redisKey, ownerToken, options.getLeaseTime());
                if (Boolean.TRUE.equals(acquired)) {
                    return Optional.of(new RedisLockHandle(
                        redis, lockName, redisKey, ownerToken));
                }
                if (!pauseBeforeRetry()) {
                    return Optional.empty();
                }
            } while (System.nanoTime() < deadline);
            return Optional.empty();
        }

        private void requireArguments(String lockName, LockOptions options) {
            if (lockName == null || lockName.isBlank()) {
                throw new IllegalArgumentException("lockName must not be blank");
            }
            Objects.requireNonNull(options, "options");
            if (options.getWaitTime() == null || options.getWaitTime().isNegative()
                || options.getLeaseTime() == null
                || options.getLeaseTime().isZero()
                || options.getLeaseTime().isNegative()) {
                throw new IllegalArgumentException("invalid distributed lock duration");
            }
        }

        private boolean pauseBeforeRetry() {
            try {
                TimeUnit.MILLISECONDS.sleep(25);
                return true;
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
    }

    private static final class RedisLockHandle implements LockHandle {
        private final StringRedisTemplate redis;
        private final String lockName;
        private final String redisKey;
        private final String ownerToken;
        private boolean closed;

        private RedisLockHandle(
            StringRedisTemplate redis,
            String lockName,
            String redisKey,
            String ownerToken
        ) {
            this.redis = redis;
            this.lockName = lockName;
            this.redisKey = redisKey;
            this.ownerToken = ownerToken;
        }

        @Override
        public String getLockName() {
            return lockName;
        }

        @Override
        public boolean isOwned() {
            return !closed && ownerToken.equals(redis.opsForValue().get(redisKey));
        }

        @Override
        public void close() {
            if (!closed) {
                redis.execute(
                    RedisDistributedLockOperations.UNLOCK_SCRIPT,
                    List.of(redisKey), ownerToken);
                closed = true;
            }
        }
    }
}
