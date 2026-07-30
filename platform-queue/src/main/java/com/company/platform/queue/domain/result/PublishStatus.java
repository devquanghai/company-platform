package com.company.platform.queue.domain.result;

public enum PublishStatus {
    PUBLISHED,
    CONFIRMED,
    ACCEPTED,
    OUTBOXED,
    RETURNED,
    REJECTED,
    TIMED_OUT,
    UNKNOWN_OUTCOME,
    FAILED
}
