package com.company.platform.queue.reliability.deadletter;

public interface DeadLetterReplayOperations {
    ReplayResult replay(String sourceDestination, ReplayCriteria criteria);
}
