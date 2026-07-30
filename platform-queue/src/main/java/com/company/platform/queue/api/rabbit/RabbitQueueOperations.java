package com.company.platform.queue.api.rabbit;

import java.time.Duration;

public interface RabbitQueueOperations {
    boolean waitForConfirms(String brokerName, Duration timeout);
}
