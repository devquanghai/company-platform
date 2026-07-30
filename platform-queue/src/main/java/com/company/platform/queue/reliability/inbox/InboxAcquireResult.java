package com.company.platform.queue.reliability.inbox;

import java.time.Instant;

public record InboxAcquireResult(
    InboxAcquireStatus status,
    String ownerId,
    long fencingToken,
    Instant lockedUntil
) {
}
