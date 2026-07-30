package com.company.platform.queue.reliability.outbox;

public enum OutboxStatus {
    PENDING,
    CLAIMED,
    PUBLISHED,
    FAILED,
    DEAD
}
