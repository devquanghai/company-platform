package com.company.platform.queue.domain.policy;

public enum RetryDecision {
    RETRY_BLOCKING,
    RETRY_DELAYED,
    DEAD_LETTER,
    REJECT,
    ACK_AND_SKIP
}
