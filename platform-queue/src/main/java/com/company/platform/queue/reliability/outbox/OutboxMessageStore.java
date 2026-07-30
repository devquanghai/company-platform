package com.company.platform.queue.reliability.outbox;

import com.company.platform.queue.api.publish.PublishResult;

import java.time.Duration;
import java.util.List;

public interface OutboxMessageStore {
    OutboxRecord save(OutboxRecord record);
    List<OutboxRecord> claimBatch(int batchSize, Duration lockTimeout);
    void renew(String outboxId, String ownerId, long fencingToken, Duration lockTimeout);
    void markPublished(
        String outboxId, String ownerId, long fencingToken, PublishResult result);
    void markFailed(
        String outboxId, String ownerId, long fencingToken, String failureCode);
}
