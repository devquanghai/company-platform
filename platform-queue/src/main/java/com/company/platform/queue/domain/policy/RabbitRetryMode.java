package com.company.platform.queue.domain.policy;

public enum RabbitRetryMode {
    BLOCKING,
    DELAYED_QUEUE,
    DEAD_LETTER_LOOP,
    NONE
}
