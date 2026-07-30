package com.company.platform.queue.reliability.inbox;

import java.time.Duration;

public interface InboxStore {
    InboxAcquireResult acquire(
        String consumerId, String messageId, Duration processingTimeout);
    void renew(
        String consumerId, String messageId, String ownerId,
        long fencingToken, Duration processingTimeout);
    void markProcessed(
        String consumerId, String messageId, String ownerId, long fencingToken);
    void markFailed(
        String consumerId, String messageId, String ownerId,
        long fencingToken, String failureCode);
}
