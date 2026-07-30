package com.company.platform.queue.api.consume;

public enum MessageHandlingResult {
    ACK,
    RETRY,
    REJECT,
    DEAD_LETTER
}
