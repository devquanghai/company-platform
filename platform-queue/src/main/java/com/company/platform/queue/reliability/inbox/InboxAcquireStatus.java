package com.company.platform.queue.reliability.inbox;

public enum InboxAcquireStatus {
    ACQUIRED,
    DUPLICATE_PROCESSED,
    PROCESSING_BY_ANOTHER,
    RETRYABLE_STALE_LOCK
}
