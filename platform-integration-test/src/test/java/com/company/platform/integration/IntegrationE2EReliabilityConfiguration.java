package com.company.platform.integration;

import com.company.platform.cache.api.lock.DistributedLockOperations;
import com.company.platform.cache.api.lock.LockHandle;
import com.company.platform.cache.api.lock.LockOptions;
import com.company.platform.queue.api.publish.PublishResult;
import com.company.platform.queue.reliability.inbox.InboxAcquireResult;
import com.company.platform.queue.reliability.inbox.InboxAcquireStatus;
import com.company.platform.queue.reliability.inbox.InboxStore;
import com.company.platform.queue.reliability.outbox.OutboxMessageStore;
import com.company.platform.queue.reliability.outbox.OutboxRecord;
import com.company.platform.queue.reliability.outbox.OutboxStatus;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@TestConfiguration(proxyBeanMethods = false)
class IntegrationE2EReliabilityConfiguration {

    @Bean
    OutboxMessageStore integrationOutboxStore() {
        return new FencedInMemoryOutboxStore();
    }

    @Bean
    InboxStore integrationInboxStore() {
        return new FencedInMemoryInboxStore();
    }

    @Bean
    DistributedLockOperations integrationDistributedLockOperations(
        @Qualifier("platformCacheRedisConnectionFactory__redis")
        RedisConnectionFactory connectionFactory
    ) {
        return new RedisIntegrationLockOperations(connectionFactory);
    }

    static final class FencedInMemoryOutboxStore implements OutboxMessageStore {
        private final Map<String, OutboxRecord> records = new LinkedHashMap<>();

        @Override
        public synchronized OutboxRecord save(OutboxRecord record) {
            if (records.putIfAbsent(record.id(), record) != null) {
                throw new IllegalStateException("duplicate outbox id");
            }
            return record;
        }

        @Override
        public synchronized List<OutboxRecord> claimBatch(
            int batchSize, Duration lockTimeout
        ) {
            Instant now = Instant.now();
            List<OutboxRecord> claimed = new ArrayList<>();
            records.values().stream()
                .filter(record -> record.status() == OutboxStatus.PENDING
                    || record.status() == OutboxStatus.FAILED)
                .filter(record -> record.availableAt() == null
                    || !record.availableAt().isAfter(now))
                .filter(record -> record.lockedUntil() == null
                    || record.lockedUntil().isBefore(now))
                .sorted(Comparator.comparing(OutboxRecord::createdAt))
                .limit(batchSize)
                .forEach(record -> {
                    OutboxRecord fenced = copy(
                        record, OutboxStatus.CLAIMED, record.attemptCount() + 1,
                        null, UUID.randomUUID().toString(),
                        record.fencingToken() + 1, now.plus(lockTimeout), null);
                    records.put(record.id(), fenced);
                    claimed.add(fenced);
                });
            return List.copyOf(claimed);
        }

        @Override
        public synchronized void renew(
            String outboxId, String ownerId, long fencingToken,
            Duration lockTimeout
        ) {
            OutboxRecord record = owned(outboxId, ownerId, fencingToken);
            records.put(outboxId, copy(
                record, record.status(), record.attemptCount(),
                record.lastErrorCode(), ownerId, fencingToken,
                Instant.now().plus(lockTimeout), record.publishedAt()));
        }

        @Override
        public synchronized void markPublished(
            String outboxId, String ownerId, long fencingToken,
            PublishResult result
        ) {
            OutboxRecord record = owned(outboxId, ownerId, fencingToken);
            records.put(outboxId, copy(
                record, OutboxStatus.PUBLISHED, record.attemptCount(),
                null, null, fencingToken, null, result.publishedAt()));
        }

        @Override
        public synchronized void markFailed(
            String outboxId, String ownerId, long fencingToken,
            String failureCode
        ) {
            OutboxRecord record = owned(outboxId, ownerId, fencingToken);
            records.put(outboxId, copy(
                record, OutboxStatus.FAILED, record.attemptCount(),
                failureCode, null, fencingToken, null, null));
        }

        synchronized OutboxRecord get(String id) {
            return records.get(id);
        }

        private OutboxRecord owned(
            String id, String ownerId, long fencingToken
        ) {
            OutboxRecord record = records.get(id);
            if (record == null || !java.util.Objects.equals(
                record.ownerId(), ownerId)
                || record.fencingToken() != fencingToken) {
                throw new IllegalStateException("stale outbox lease");
            }
            return record;
        }

        private OutboxRecord copy(
            OutboxRecord source, OutboxStatus status, int attempts,
            String error, String owner, long token, Instant lockedUntil,
            Instant publishedAt
        ) {
            return new OutboxRecord(
                source.id(), source.aggregateType(), source.aggregateId(),
                source.destination(), source.messageKey(), source.messageId(),
                source.eventType(), source.schemaVersion(), source.payload(),
                source.headers(), source.createdAt(), source.availableAt(),
                publishedAt, status, attempts, error, owner, token, lockedUntil);
        }
    }

    static final class FencedInMemoryInboxStore implements InboxStore {
        private final Map<String, InboxEntry> entries = new LinkedHashMap<>();

        @Override
        public synchronized InboxAcquireResult acquire(
            String consumerId, String messageId, Duration processingTimeout
        ) {
            String key = consumerId + '\u0000' + messageId;
            Instant now = Instant.now();
            InboxEntry existing = entries.get(key);
            if (existing != null && existing.processed()) {
                return new InboxAcquireResult(
                    InboxAcquireStatus.DUPLICATE_PROCESSED, null,
                    existing.token(), null);
            }
            if (existing != null && existing.lockedUntil().isAfter(now)) {
                return new InboxAcquireResult(
                    InboxAcquireStatus.PROCESSING_BY_ANOTHER, existing.owner(),
                    existing.token(), existing.lockedUntil());
            }
            String owner = UUID.randomUUID().toString();
            long token = existing == null ? 1 : existing.token() + 1;
            Instant lockedUntil = now.plus(processingTimeout);
            entries.put(key, new InboxEntry(owner, token, lockedUntil, false));
            return new InboxAcquireResult(
                existing == null ? InboxAcquireStatus.ACQUIRED
                    : InboxAcquireStatus.RETRYABLE_STALE_LOCK,
                owner, token, lockedUntil);
        }

        @Override
        public synchronized void renew(
            String consumerId, String messageId, String ownerId,
            long fencingToken, Duration processingTimeout
        ) {
            String key = consumerId + '\u0000' + messageId;
            owned(key, ownerId, fencingToken);
            entries.put(key, new InboxEntry(
                ownerId, fencingToken, Instant.now().plus(processingTimeout), false));
        }

        @Override
        public synchronized void markProcessed(
            String consumerId, String messageId, String ownerId,
            long fencingToken
        ) {
            String key = consumerId + '\u0000' + messageId;
            owned(key, ownerId, fencingToken);
            entries.put(key, new InboxEntry(
                ownerId, fencingToken, Instant.EPOCH, true));
        }

        @Override
        public synchronized void markFailed(
            String consumerId, String messageId, String ownerId,
            long fencingToken, String failureCode
        ) {
            String key = consumerId + '\u0000' + messageId;
            owned(key, ownerId, fencingToken);
            entries.remove(key);
        }

        private void owned(String key, String ownerId, long fencingToken) {
            InboxEntry entry = entries.get(key);
            if (entry == null || !entry.owner().equals(ownerId)
                || entry.token() != fencingToken) {
                throw new IllegalStateException("stale inbox lease");
            }
        }

        private record InboxEntry(
            String owner, long token, Instant lockedUntil, boolean processed
        ) {
        }
    }

    static final class RedisIntegrationLockOperations
        implements DistributedLockOperations {
        private static final DefaultRedisScript<Long> UNLOCK =
            new DefaultRedisScript<>("""
                if redis.call('get', KEYS[1]) == ARGV[1] then
                  return redis.call('del', KEYS[1])
                end
                return 0
                """, Long.class);
        private final StringRedisTemplate redis;

        RedisIntegrationLockOperations(RedisConnectionFactory connectionFactory) {
            redis = new StringRedisTemplate(connectionFactory);
            redis.afterPropertiesSet();
        }

        @Override
        public <T> T executeWithLock(
            String lockName, LockOptions options, Supplier<T> action
        ) {
            LockHandle handle = tryLock(lockName, options)
                .orElseThrow(() -> new IllegalStateException(
                    "integration Redis lock timeout"));
            try (handle) {
                return action.get();
            }
        }

        @Override
        public Optional<LockHandle> tryLock(
            String lockName, LockOptions options
        ) {
            if (lockName == null || lockName.isBlank()) {
                throw new IllegalArgumentException("lockName must not be blank");
            }
            String key = "platform-integration:lock:" + lockName;
            String token = UUID.randomUUID().toString();
            long deadline = System.nanoTime() + options.getWaitTime().toNanos();
            do {
                Boolean acquired = redis.opsForValue().setIfAbsent(
                    key, token, options.getLeaseTime());
                if (Boolean.TRUE.equals(acquired)) {
                    return Optional.of(new RedisLockHandle(redis, key, token));
                }
                try {
                    TimeUnit.MILLISECONDS.sleep(10);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    return Optional.empty();
                }
            } while (System.nanoTime() < deadline);
            return Optional.empty();
        }

        private static final class RedisLockHandle implements LockHandle {
            private final StringRedisTemplate redis;
            private final String key;
            private final String token;
            private boolean owned = true;

            private RedisLockHandle(
                StringRedisTemplate redis, String key, String token
            ) {
                this.redis = redis;
                this.key = key;
                this.token = token;
            }

            @Override
            public String getLockName() {
                return key.substring(key.lastIndexOf(':') + 1);
            }

            @Override
            public boolean isOwned() {
                return owned;
            }

            @Override
            public void close() {
                if (owned) {
                    redis.execute(UNLOCK, List.of(key), token);
                    owned = false;
                }
            }
        }
    }
}
