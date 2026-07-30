package com.company.platform.queue.reliability.deadletter;

public record ReplayResult(
    ReplayStatus status,
    int inspected,
    int replayed,
    int skipped,
    int failed
) {
}
