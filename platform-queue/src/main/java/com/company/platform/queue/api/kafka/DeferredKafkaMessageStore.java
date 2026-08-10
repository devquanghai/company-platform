package com.company.platform.queue.api.kafka;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Durable staging port for BATCH/BULK consumers.
 *
 * <p>The adapter must enforce uniqueness by subscription/topic/partition/offset.
 * A claimed batch must contain contiguous offsets from exactly one partition,
 * ordered ascending, and become ready when count OR oldest-message age reaches
 * the configured threshold. Only one active claim is allowed per
 * subscription/topic/partition. A retry-delayed head blocks newer offsets in
 * that partition. Claim and terminal updates must use fencing.</p>
 */
public interface DeferredKafkaMessageStore {
    DeferredKafkaStageResult stage(DeferredKafkaMessage message);

    Optional<DeferredKafkaBatch> claimReady(
        String subscription,
        int maximumMessages,
        Duration maximumWait,
        Duration lockTimeout,
        Instant now);

    void renewClaim(
        String claimId, String ownerId, long fencingToken, Duration lockTimeout);

    void markCompleted(String claimId, String ownerId, long fencingToken);

    void release(
        String claimId, String ownerId, long fencingToken,
        Instant retryAt, String failureCode);

    /** Release without incrementing the delivery attempt; used for active-owner contention. */
    void releaseContended(
        String claimId, String ownerId, long fencingToken, Instant retryAt);

    /**
     * Atomically completes the successful prefix, dead-letters only the failed
     * head, preserves the untouched suffix, and keeps newer partition offsets
     * blocked until the transition commits.
     */
    void markDeadLetter(
        String claimId, String ownerId, long fencingToken,
        int successfullyProcessedMessages, String failureCode);
}
