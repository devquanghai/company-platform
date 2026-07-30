package com.company.platform.queue.reliability.deadletter;

import java.time.Instant;

public record ReplayCriteria(
    int maxMessages,
    int messagesPerSecond,
    Instant failedAfter,
    boolean dryRun
) {
    public ReplayCriteria {
        if (maxMessages < 1 || messagesPerSecond < 1) {
            throw new IllegalArgumentException("replay limits must be positive");
        }
    }
}
