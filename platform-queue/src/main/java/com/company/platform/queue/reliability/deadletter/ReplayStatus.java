package com.company.platform.queue.reliability.deadletter;

public enum ReplayStatus {
    REPLAYED,
    SKIPPED,
    INVALID,
    FAILED
}
